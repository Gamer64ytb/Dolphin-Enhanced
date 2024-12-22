// Copyright 2016 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "VideoBackends/Vulkan/ShaderCompiler.h"
#include "VideoBackends/Vulkan/VulkanContext.h"

#include <cstddef>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <memory>
#include <string>

// glslang includes
#include "GlslangToSpv.h"
#include "ResourceLimits.h"
#include "ShaderLang.h"
#include "disassemble.h"

#include "Common/FileUtil.h"
#include "Common/Logging/Log.h"
#include "Common/MsgHandler.h"
#include "Common/StringUtil.h"

#include "VideoCommon/VideoConfig.h"

namespace Vulkan::ShaderCompiler
{
// Registers itself for cleanup via atexit
bool InitializeGlslang();

// Resource limits used when compiling shaders
static const TBuiltInResource* GetCompilerResourceLimits();

// Regarding the UBO bind points, we subtract one from the binding index because
// the OpenGL backend requires UBO #0 for non-block uniforms (at least on NV).
// This allows us to share the same shaders but use bind point #0 in the Vulkan
// backend. None of the Vulkan-specific shaders use UBOs, instead they use push
// constants, so when/if the GL backend moves to uniform blocks completely this
// subtraction can be removed.
static const char SHADER_HEADER[] = R"(
  // Target GLSL 4.5.
  #version 450 core
  #define ATTRIBUTE_LOCATION(x) layout(location = x)
  #define FRAGMENT_OUTPUT_LOCATION(x) layout(location = x)
  #define FRAGMENT_OUTPUT_LOCATION_INDEXED(x, y) layout(location = x, index = y)
  #define UBO_BINDING(packing, x) layout(packing, set = 0, binding = (x - 1))
  #define SAMPLER_BINDING(x) layout(set = 1, binding = x)
  #define TEXEL_BUFFER_BINDING(x) layout(set = 1, binding = (x + 8))
  #define SSBO_BINDING(x) layout(set = 2, binding = x)
  #define VARYING_LOCATION(x) layout(location = x)
  #define FORCE_EARLY_Z layout(early_fragment_tests) in

  // hlsl to glsl function translation
  #define API_VULKAN 1
  #define float2 vec2
  #define float3 vec3
  #define float4 vec4
  #define uint2 uvec2
  #define uint3 uvec3
  #define uint4 uvec4
  #define int2 ivec2
  #define int3 ivec3
  #define int4 ivec4
  #define frac fract
  #define lerp mix

  // These were changed in Vulkan
  #define gl_VertexID gl_VertexIndex
  #define gl_InstanceID gl_InstanceIndex
)";
static const char COMPUTE_SHADER_HEADER[] = R"(
  // Target GLSL 4.5.
  #version 450 core
  // All resources are packed into one descriptor set for compute.
  #define UBO_BINDING(packing, x) layout(packing, set = 0, binding = (x - 1))
  #define SAMPLER_BINDING(x) layout(set = 0, binding = (1 + x))
  #define TEXEL_BUFFER_BINDING(x) layout(set = 0, binding = (3 + x))
  #define IMAGE_BINDING(format, x) layout(format, set = 0, binding = (5 + x))

  // hlsl to glsl function translation
  #define API_VULKAN 1
  #define float2 vec2
  #define float3 vec3
  #define float4 vec4
  #define uint2 uvec2
  #define uint3 uvec3
  #define uint4 uvec4
  #define int2 ivec2
  #define int3 ivec3
  #define int4 ivec4
  #define frac fract
  #define lerp mix
)";
static const char SUBGROUP_HELPER_HEADER[] = R"(
  #extension GL_KHR_shader_subgroup_basic : enable
  #extension GL_KHR_shader_subgroup_arithmetic : enable
  #extension GL_KHR_shader_subgroup_ballot : enable

  #define SUPPORTS_SUBGROUP_REDUCTION 1
  #define CAN_USE_SUBGROUP_REDUCTION true
  #define IS_HELPER_INVOCATION gl_HelperInvocation
  #define IS_FIRST_ACTIVE_INVOCATION (gl_SubgroupInvocationID == subgroupBallotFindLSB(subgroupBallot(true)))
  #define SUBGROUP_MIN(value) value = subgroupMin(value)
  #define SUBGROUP_MAX(value) value = subgroupMax(value)
)";

std::optional<SPIRVCodeVector> CompileShaderToSPV(EShLanguage stage, const char* stage_filename,
                                                  std::string_view source, std::string_view header)
{
  if (!InitializeGlslang())
    return std::nullopt;

  std::unique_ptr<glslang::TShader> shader = std::make_unique<glslang::TShader>(stage);
  std::unique_ptr<glslang::TProgram> program;
  glslang::TShader::ForbidIncluder includer;
  EProfile profile = ECoreProfile;
  EShMessages messages =
      static_cast<EShMessages>(EShMsgDefault | EShMsgSpvRules | EShMsgVulkanRules);
  int default_version = 450;

  std::string full_source_code;
  const char* pass_source_code = source.data();
  int pass_source_code_length = static_cast<int>(source.size());
  if (!header.empty())
  {
    constexpr size_t subgroup_helper_header_length = std::size(SUBGROUP_HELPER_HEADER) - 1;
    full_source_code.reserve(header.size() + subgroup_helper_header_length + source.size());
    full_source_code.append(header);
    if (g_vulkan_context->SupportsShaderSubgroupOperations())
      full_source_code.append(SUBGROUP_HELPER_HEADER, subgroup_helper_header_length);
    full_source_code.append(source);
    pass_source_code = full_source_code.c_str();
    pass_source_code_length = static_cast<int>(full_source_code.length());
  }

  // Sub-group operations require Vulkan 1.1 and SPIR-V 1.3.
  if (g_vulkan_context->SupportsShaderSubgroupOperations())
    shader->setEnvTarget(glslang::EShTargetSpv, glslang::EShTargetSpv_1_3);

  shader->setStringsWithLengths(&pass_source_code, &pass_source_code_length, 1);

  auto DumpBadShader = [&](const char* msg) {
    static int counter = 0;
    std::string filename = StringFromFormat(
        "%sbad_%s_%04i.txt", File::GetUserPath(D_DUMP_IDX).c_str(), stage_filename, counter++);

    std::ofstream stream;
    File::OpenFStream(stream, filename, std::ios_base::out);
    if (stream.good())
    {
      stream << full_source_code << std::endl;
      stream << msg << std::endl;
      stream << "Shader Info Log:" << std::endl;
      stream << shader->getInfoLog() << std::endl;
      stream << shader->getInfoDebugLog() << std::endl;
      if (program)
      {
        stream << "Program Info Log:" << std::endl;
        stream << program->getInfoLog() << std::endl;
        stream << program->getInfoDebugLog() << std::endl;
      }
    }

    PanicAlert("%s (written to %s)", msg, filename.c_str());
  };

  if (!shader->parse(GetCompilerResourceLimits(), default_version, profile, false, true, messages,
                     includer))
  {
    DumpBadShader("Failed to parse shader");
    return std::nullopt;
  }

  // Even though there's only a single shader, we still need to link it to generate SPV
  program = std::make_unique<glslang::TProgram>();
  program->addShader(shader.get());
  if (!program->link(messages))
  {
    DumpBadShader("Failed to link program");
    return std::nullopt;
  }

  glslang::TIntermediate* intermediate = program->getIntermediate(stage);
  if (!intermediate)
  {
    DumpBadShader("Failed to generate SPIR-V");
    return std::nullopt;
  }

  SPIRVCodeVector out_code;
  spv::SpvBuildLogger logger;
  glslang::SpvOptions options;
  options.disableOptimizer = false;
  options.optimizeSize = false;
  options.generateDebugInfo = false;
  glslang::GlslangToSpv(*intermediate, out_code, &logger, &options);

  // Write out messages
  // Temporary: skip if it contains "Warning, version 450 is not yet complete; most version-specific
  // features are present, but some are missing."
  if (strlen(shader->getInfoLog()) > 108)
    WARN_LOG(VIDEO, "Shader info log: %s", shader->getInfoLog());
  if (strlen(shader->getInfoDebugLog()) > 0)
    WARN_LOG(VIDEO, "Shader debug info log: %s", shader->getInfoDebugLog());
  if (strlen(program->getInfoLog()) > 25)
    WARN_LOG(VIDEO, "Program info log: %s", program->getInfoLog());
  if (strlen(program->getInfoDebugLog()) > 0)
    WARN_LOG(VIDEO, "Program debug info log: %s", program->getInfoDebugLog());
  std::string spv_messages = logger.getAllMessages();
  if (!spv_messages.empty())
    WARN_LOG(VIDEO, "SPIR-V conversion messages: %s", spv_messages.c_str());

  // Dump source code of shaders out to file if enabled.
  if (g_ActiveConfig.iLog & CONF_SAVESHADERS)
  {
    static int counter = 0;
    std::string filename = StringFromFormat("%s%s_%04i.txt", File::GetUserPath(D_DUMP_IDX).c_str(),
                                            stage_filename, counter++);

    std::ofstream stream;
    File::OpenFStream(stream, filename, std::ios_base::out);
    if (stream.good())
    {
      stream << full_source_code << std::endl;
      stream << "Shader Info Log:" << std::endl;
      stream << shader->getInfoLog() << std::endl;
      stream << shader->getInfoDebugLog() << std::endl;
      stream << "Program Info Log:" << std::endl;
      stream << program->getInfoLog() << std::endl;
      stream << program->getInfoDebugLog() << std::endl;
      stream << "SPIR-V conversion messages: " << std::endl;
      stream << spv_messages;
      stream << "SPIR-V:" << std::endl;
      spv::Disassemble(stream, out_code);
    }
  }

  return out_code;
}

bool InitializeGlslang()
{
  static bool glslang_initialized = false;
  if (glslang_initialized)
    return true;

  if (!glslang::InitializeProcess())
  {
    PanicAlert("Failed to initialize glslang shader compiler");
    return false;
  }

  std::atexit([]() { glslang::FinalizeProcess(); });

  glslang_initialized = true;
  return true;
}

const TBuiltInResource* GetCompilerResourceLimits()
{
  return &glslang::DefaultTBuiltInResource;
}

std::optional<SPIRVCodeVector> CompileVertexShader(std::string_view source_code)
{
  return CompileShaderToSPV(EShLangVertex, "vs", source_code, SHADER_HEADER);
}

std::optional<SPIRVCodeVector> CompileGeometryShader(std::string_view source_code)
{
  return CompileShaderToSPV(EShLangGeometry, "gs", source_code, SHADER_HEADER);
}

std::optional<SPIRVCodeVector> CompileFragmentShader(std::string_view source_code)
{
  return CompileShaderToSPV(EShLangFragment, "ps", source_code, SHADER_HEADER);
}

std::optional<SPIRVCodeVector> CompileComputeShader(std::string_view source_code)
{
  return CompileShaderToSPV(EShLangCompute, "cs", source_code, COMPUTE_SHADER_HEADER);
}
}  // namespace Vulkan::ShaderCompiler
