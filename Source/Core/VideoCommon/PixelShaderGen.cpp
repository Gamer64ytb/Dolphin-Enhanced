// Copyright 2008 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "VideoCommon/PixelShaderGen.h"

#include <cmath>
#include <cstdio>

#include "Common/Assert.h"
#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"
#include "VideoCommon/BPMemory.h"
#include "VideoCommon/BoundingBox.h"
#include "VideoCommon/DriverDetails.h"
#include "VideoCommon/LightingShaderGen.h"
#include "VideoCommon/NativeVertexFormat.h"
#include "VideoCommon/RenderState.h"
#include "VideoCommon/VertexLoaderManager.h"
#include "VideoCommon/VideoCommon.h"
#include "VideoCommon/VideoConfig.h"
#include "VideoCommon/XFMemory.h"  // for texture projection mode

// TODO: Get rid of these
enum : u32
{
  C_COLORMATRIX = 0,                //  0
  C_COLORS = 0,                     //  0
  C_KCOLORS = C_COLORS + 4,         //  4
  C_ALPHA = C_KCOLORS + 4,          //  8
  C_TEXDIMS = C_ALPHA + 1,          //  9
  C_ZBIAS = C_TEXDIMS + 8,          // 17
  C_INDTEXSCALE = C_ZBIAS + 2,      // 19
  C_INDTEXMTX = C_INDTEXSCALE + 2,  // 21
  C_FOGCOLOR = C_INDTEXMTX + 6,     // 27
  C_FOGI = C_FOGCOLOR + 1,          // 28
  C_FOGF = C_FOGI + 1,              // 29
  C_ZSLOPE = C_FOGF + 2,            // 31
  C_EFBSCALE = C_ZSLOPE + 1,        // 32
  C_PENVCONST_END = C_EFBSCALE + 1
};

constexpr std::array<const char*, 32> tev_ksel_table_c{
    "255,255,255",        // 1   = 0x00
    "223,223,223",        // 7_8 = 0x01
    "191,191,191",        // 3_4 = 0x02
    "159,159,159",        // 5_8 = 0x03
    "128,128,128",        // 1_2 = 0x04
    "96,96,96",           // 3_8 = 0x05
    "64,64,64",           // 1_4 = 0x06
    "32,32,32",           // 1_8 = 0x07
    "0,0,0",              // INVALID = 0x08
    "0,0,0",              // INVALID = 0x09
    "0,0,0",              // INVALID = 0x0a
    "0,0,0",              // INVALID = 0x0b
    I_KCOLORS "[0].rgb",  // K0 = 0x0C
    I_KCOLORS "[1].rgb",  // K1 = 0x0D
    I_KCOLORS "[2].rgb",  // K2 = 0x0E
    I_KCOLORS "[3].rgb",  // K3 = 0x0F
    I_KCOLORS "[0].rrr",  // K0_R = 0x10
    I_KCOLORS "[1].rrr",  // K1_R = 0x11
    I_KCOLORS "[2].rrr",  // K2_R = 0x12
    I_KCOLORS "[3].rrr",  // K3_R = 0x13
    I_KCOLORS "[0].ggg",  // K0_G = 0x14
    I_KCOLORS "[1].ggg",  // K1_G = 0x15
    I_KCOLORS "[2].ggg",  // K2_G = 0x16
    I_KCOLORS "[3].ggg",  // K3_G = 0x17
    I_KCOLORS "[0].bbb",  // K0_B = 0x18
    I_KCOLORS "[1].bbb",  // K1_B = 0x19
    I_KCOLORS "[2].bbb",  // K2_B = 0x1A
    I_KCOLORS "[3].bbb",  // K3_B = 0x1B
    I_KCOLORS "[0].aaa",  // K0_A = 0x1C
    I_KCOLORS "[1].aaa",  // K1_A = 0x1D
    I_KCOLORS "[2].aaa",  // K2_A = 0x1E
    I_KCOLORS "[3].aaa",  // K3_A = 0x1F
};

constexpr std::array<const char*, 32> tev_ksel_table_a{
    "255",              // 1   = 0x00
    "223",              // 7_8 = 0x01
    "191",              // 3_4 = 0x02
    "159",              // 5_8 = 0x03
    "128",              // 1_2 = 0x04
    "96",               // 3_8 = 0x05
    "64",               // 1_4 = 0x06
    "32",               // 1_8 = 0x07
    "0",                // INVALID = 0x08
    "0",                // INVALID = 0x09
    "0",                // INVALID = 0x0a
    "0",                // INVALID = 0x0b
    "0",                // INVALID = 0x0c
    "0",                // INVALID = 0x0d
    "0",                // INVALID = 0x0e
    "0",                // INVALID = 0x0f
    I_KCOLORS "[0].r",  // K0_R = 0x10
    I_KCOLORS "[1].r",  // K1_R = 0x11
    I_KCOLORS "[2].r",  // K2_R = 0x12
    I_KCOLORS "[3].r",  // K3_R = 0x13
    I_KCOLORS "[0].g",  // K0_G = 0x14
    I_KCOLORS "[1].g",  // K1_G = 0x15
    I_KCOLORS "[2].g",  // K2_G = 0x16
    I_KCOLORS "[3].g",  // K3_G = 0x17
    I_KCOLORS "[0].b",  // K0_B = 0x18
    I_KCOLORS "[1].b",  // K1_B = 0x19
    I_KCOLORS "[2].b",  // K2_B = 0x1A
    I_KCOLORS "[3].b",  // K3_B = 0x1B
    I_KCOLORS "[0].a",  // K0_A = 0x1C
    I_KCOLORS "[1].a",  // K1_A = 0x1D
    I_KCOLORS "[2].a",  // K2_A = 0x1E
    I_KCOLORS "[3].a",  // K3_A = 0x1F
};

constexpr std::array<const char*, 16> tev_c_input_table{
    "prev.rgb",           // CPREV,
    "prev.aaa",           // APREV,
    "c0.rgb",             // C0,
    "c0.aaa",             // A0,
    "c1.rgb",             // C1,
    "c1.aaa",             // A1,
    "c2.rgb",             // C2,
    "c2.aaa",             // A2,
    "textemp.rgb",        // TEXC,
    "textemp.aaa",        // TEXA,
    "rastemp.rgb",        // RASC,
    "rastemp.aaa",        // RASA,
    "int3(255,255,255)",  // ONE
    "int3(128,128,128)",  // HALF
    "konsttemp.rgb",      // KONST
    "int3(0,0,0)",        // ZERO
};

constexpr std::array<const char*, 8> tev_a_input_table{
    "prev.a",       // APREV,
    "c0.a",         // A0,
    "c1.a",         // A1,
    "c2.a",         // A2,
    "textemp.a",    // TEXA,
    "rastemp.a",    // RASA,
    "konsttemp.a",  // KONST,  (hw1 had quarter)
    "0",            // ZERO
};

constexpr std::array<const char*, 8> tev_ras_table{
    "iround(col0 * 255.0)",
    "iround(col1 * 255.0)",
    "ERROR13",                                              // 2
    "ERROR14",                                              // 3
    "ERROR15",                                              // 4
    "(int4(1, 1, 1, 1) * alphabump)",                       // bump alpha (0..248)
    "(int4(1, 1, 1, 1) * (alphabump | (alphabump >> 5)))",  // normalized bump alpha (0..255)
    "int4(0, 0, 0, 0)",                                     // zero
};

constexpr std::array<const char*, 4> tev_output_table{
    "prev",
    "c0",
    "c1",
    "c2",
};

constexpr std::array<const char*, 4> tev_c_output_table{
    "prev.rgb",
    "c0.rgb",
    "c1.rgb",
    "c2.rgb",
};

constexpr std::array<const char*, 4> tev_a_output_table{
    "prev.a",
    "c0.a",
    "c1.a",
    "c2.a",
};

// FIXME: Some of the video card's capabilities (BBox support, EarlyZ support, dstAlpha support)
//        leak into this UID; This is really unhelpful if these UIDs ever move from one machine to
//        another.
PixelShaderUid GetPixelShaderUid()
{
  PixelShaderUid out;

  pixel_shader_uid_data* const uid_data = out.GetUidData();

  uid_data->genMode_numindstages = bpmem.genMode.numindstages;
  uid_data->genMode_numtevstages = bpmem.genMode.numtevstages;
  uid_data->genMode_numtexgens = bpmem.genMode.numtexgens;
  uid_data->bounding_box = g_ActiveConfig.bBBoxEnable && BoundingBox::IsEnabled();
  uid_data->rgba6_format =
      bpmem.zcontrol.pixel_format == PEControl::RGBA6_Z24 && !g_ActiveConfig.bForceTrueColor;
  uid_data->dither = bpmem.blendmode.dither && uid_data->rgba6_format;

  // OpenGL and Vulkan convert implicitly normalized color outputs to their uint representation.
  // Therefore, it is not necessary to use a uint output on these backends. We also disable the
  // uint output when logic op is not supported (i.e. driver/device does not support D3D11.1).
  if (g_ActiveConfig.backend_info.api_type == APIType::D3D &&
      g_ActiveConfig.backend_info.bSupportsLogicOp)
    uid_data->uint_output = bpmem.blendmode.UseLogicOp();

  u32 numStages = uid_data->genMode_numtevstages + 1;

  if (g_ActiveConfig.bEnablePixelLighting)
  {
    // The lighting shader only needs the two color bits of the 23bit component bit array.
    uid_data->components =
        (VertexLoaderManager::g_current_components & (VB_HAS_COL0 | VB_HAS_COL1)) >> VB_COL_SHIFT;
    uid_data->numColorChans = xfmem.numChan.numColorChans;
    GetLightingShaderUid(uid_data->lighting);
  }

  if (uid_data->genMode_numtexgens > 0)
  {
    for (unsigned int i = 0; i < uid_data->genMode_numtexgens; ++i)
    {
      // optional perspective divides
      uid_data->texMtxInfo_n_projection |= xfmem.texMtxInfo[i].projection << i;
    }
  }

  // indirect texture map lookup
  int nIndirectStagesUsed = 0;
  if (uid_data->genMode_numindstages > 0)
  {
    for (unsigned int i = 0; i < numStages; ++i)
    {
      if (bpmem.tevind[i].IsActive() && bpmem.tevind[i].bt < uid_data->genMode_numindstages)
        nIndirectStagesUsed |= 1 << bpmem.tevind[i].bt;
    }
  }

  uid_data->nIndirectStagesUsed = nIndirectStagesUsed;
  for (u32 i = 0; i < uid_data->genMode_numindstages; ++i)
  {
    if (uid_data->nIndirectStagesUsed & (1 << i))
      uid_data->SetTevindrefValues(i, bpmem.tevindref.getTexCoord(i), bpmem.tevindref.getTexMap(i));
  }

  for (unsigned int n = 0; n < numStages; n++)
  {
    int texcoord = bpmem.tevorders[n / 2].getTexCoord(n & 1);
    bool bHasTexCoord = (u32)texcoord < bpmem.genMode.numtexgens;
    // HACK to handle cases where the tex gen is not enabled
    if (!bHasTexCoord)
      texcoord = bpmem.genMode.numtexgens;

    uid_data->stagehash[n].hasindstage = bpmem.tevind[n].bt < bpmem.genMode.numindstages;
    uid_data->stagehash[n].tevorders_texcoord = texcoord;
    if (uid_data->stagehash[n].hasindstage)
      uid_data->stagehash[n].tevind = bpmem.tevind[n].hex;

    TevStageCombiner::ColorCombiner& cc = bpmem.combiners[n].colorC;
    TevStageCombiner::AlphaCombiner& ac = bpmem.combiners[n].alphaC;
    uid_data->stagehash[n].cc = cc.hex & 0xFFFFFF;
    uid_data->stagehash[n].ac = ac.hex & 0xFFFFF0;  // Storing rswap and tswap later

    if (cc.a == TEVCOLORARG_RASA || cc.a == TEVCOLORARG_RASC || cc.b == TEVCOLORARG_RASA ||
        cc.b == TEVCOLORARG_RASC || cc.c == TEVCOLORARG_RASA || cc.c == TEVCOLORARG_RASC ||
        cc.d == TEVCOLORARG_RASA || cc.d == TEVCOLORARG_RASC || ac.a == TEVALPHAARG_RASA ||
        ac.b == TEVALPHAARG_RASA || ac.c == TEVALPHAARG_RASA || ac.d == TEVALPHAARG_RASA)
    {
      const int i = bpmem.combiners[n].alphaC.rswap;
      uid_data->stagehash[n].tevksel_swap1a = bpmem.tevksel[i * 2].swap1;
      uid_data->stagehash[n].tevksel_swap2a = bpmem.tevksel[i * 2].swap2;
      uid_data->stagehash[n].tevksel_swap1b = bpmem.tevksel[i * 2 + 1].swap1;
      uid_data->stagehash[n].tevksel_swap2b = bpmem.tevksel[i * 2 + 1].swap2;
      uid_data->stagehash[n].tevorders_colorchan = bpmem.tevorders[n / 2].getColorChan(n & 1);
    }

    uid_data->stagehash[n].tevorders_enable = bpmem.tevorders[n / 2].getEnable(n & 1);
    if (uid_data->stagehash[n].tevorders_enable)
    {
      const int i = bpmem.combiners[n].alphaC.tswap;
      uid_data->stagehash[n].tevksel_swap1c = bpmem.tevksel[i * 2].swap1;
      uid_data->stagehash[n].tevksel_swap2c = bpmem.tevksel[i * 2].swap2;
      uid_data->stagehash[n].tevksel_swap1d = bpmem.tevksel[i * 2 + 1].swap1;
      uid_data->stagehash[n].tevksel_swap2d = bpmem.tevksel[i * 2 + 1].swap2;
      uid_data->stagehash[n].tevorders_texmap = bpmem.tevorders[n / 2].getTexMap(n & 1);
    }

    if (cc.a == TEVCOLORARG_KONST || cc.b == TEVCOLORARG_KONST || cc.c == TEVCOLORARG_KONST ||
        cc.d == TEVCOLORARG_KONST || ac.a == TEVALPHAARG_KONST || ac.b == TEVALPHAARG_KONST ||
        ac.c == TEVALPHAARG_KONST || ac.d == TEVALPHAARG_KONST)
    {
      uid_data->stagehash[n].tevksel_kc = bpmem.tevksel[n / 2].getKC(n & 1);
      uid_data->stagehash[n].tevksel_ka = bpmem.tevksel[n / 2].getKA(n & 1);
    }
  }

#define MY_STRUCT_OFFSET(str, elem) ((u32)((u64) & (str).elem - (u64) & (str)))
  uid_data->num_values = (g_ActiveConfig.bEnablePixelLighting) ?
                             sizeof(*uid_data) :
                             MY_STRUCT_OFFSET(*uid_data, stagehash[numStages]);

  AlphaTest::TEST_RESULT Pretest = bpmem.alpha_test.TestResult();
  uid_data->Pretest = Pretest;

  uid_data->zfreeze = bpmem.genMode.zfreeze;
  uid_data->ztex_op = bpmem.ztex2.op;

  if (bpmem.zmode.testenable)
  {
    uid_data->early_ztest = bpmem.zcontrol.early_ztest;
    uid_data->late_ztest = !bpmem.zcontrol.early_ztest;

    // We can't allow early_ztest for zfreeze because depth is overridden per-pixel.
    // This means it's impossible for zcomploc to be emulated on a zfrozen polygon.
    const bool forced_early_z = uid_data->early_ztest &&
                                (g_ActiveConfig.bFastDepthCalc ||
                                 bpmem.alpha_test.TestResult() == AlphaTest::UNDETERMINED) &&
                                !bpmem.genMode.zfreeze;
    const bool per_pixel_depth = (bpmem.ztex2.op != ZTEXTURE_DISABLE && uid_data->late_ztest) ||
                                 (!g_ActiveConfig.bFastDepthCalc && !forced_early_z) ||
                                 bpmem.genMode.zfreeze;

    uid_data->per_pixel_depth = per_pixel_depth;
    uid_data->forced_early_z = forced_early_z;
  }
  else
  {
    uid_data->forced_early_z = true;
  }

  // NOTE: Fragment may not be discarded if alpha test always fails and early depth test is enabled
  // (in this case we need to write a depth value if depth test passes regardless of the alpha
  // testing result)
  if (uid_data->Pretest == AlphaTest::UNDETERMINED ||
      (uid_data->Pretest == AlphaTest::FAIL && uid_data->late_ztest))
  {
    uid_data->alpha_test_comp0 = bpmem.alpha_test.comp0;
    uid_data->alpha_test_comp1 = bpmem.alpha_test.comp1;
    uid_data->alpha_test_logic = bpmem.alpha_test.logic;

    // ZCOMPLOC HACK:
    // The only way to emulate alpha test + early-z is to force early-z in the shader.
    // As this isn't available on all drivers and as we can't emulate this feature otherwise,
    // we are only able to choose which one we want to respect more.
    // Tests seem to have proven that writing depth even when the alpha test fails is more
    // important that a reliable alpha test, so we just force the alpha test to always succeed.
    // At least this seems to be less buggy.
    uid_data->alpha_test_use_zcomploc_hack = uid_data->early_ztest && bpmem.zmode.updateenable &&
                                             !g_ActiveConfig.backend_info.bSupportsEarlyZ &&
                                             !bpmem.genMode.zfreeze;
  }

  uid_data->fog_fsel = bpmem.fog.c_proj_fsel.fsel;
  uid_data->fog_fsel = bpmem.fog.c_proj_fsel.fsel;
  uid_data->fog_proj = bpmem.fog.c_proj_fsel.proj;
  uid_data->fog_RangeBaseEnabled = bpmem.fogRange.Base.Enabled;

  BlendingState state = {};
  state.Generate(bpmem);

  uid_data->useDstAlpha = bpmem.dstalpha.enable && bpmem.blendmode.alphaupdate &&
                          bpmem.zcontrol.pixel_format == PEControl::RGBA6_Z24;

  if (state.logicopenable && state.logicmode != BlendMode::LogicOp::COPY &&
      !g_ActiveConfig.backend_info.bSupportsLogicOp)
  {
    // shader logic ops
    if (g_ActiveConfig.backend_info.bSupportsFramebufferFetch)
    {
      uid_data->logic_op_enable = state.logicopenable;
      uid_data->logic_mode = state.logicmode;
    }
    else if (state.logicmode == BlendMode::LogicOp::CLEAR ||
             state.logicmode == BlendMode::LogicOp::COPY_INVERTED ||
             state.logicmode == BlendMode::LogicOp::SET)
    {
      uid_data->logic_op_enable = state.logicopenable;
      uid_data->logic_mode = state.logicmode;
    }
  }

  if (state.IsDualSourceBlend())
  {
    if (g_ActiveConfig.backend_info.bSupportsDualSourceBlend)
    {
      uid_data->dualSrcBlend = true;
    }
    else
    {
      // before alpha pass
      uid_data->useDstAlpha = false;
    }
  }

  return out;
}

void ClearUnusedPixelShaderUidBits(APIType ApiType, const ShaderHostConfig& host_config,
                                   PixelShaderUid* uid)
{
  pixel_shader_uid_data* const uid_data = uid->GetUidData();

  // OpenGL and Vulkan convert implicitly normalized color outputs to their uint representation.
  // Therefore, it is not necessary to use a uint output on these backends. We also disable the
  // uint output when logic op is not supported (i.e. driver/device does not support D3D11.1).
  if (ApiType != APIType::D3D || !host_config.backend_logic_op)
    uid_data->uint_output = 0;

  if (!host_config.backend_dual_source_blend)
  {
    uid_data->dualSrcBlend = 0;
  }

  if (host_config.backend_logic_op)
  {
    uid_data->logic_op_enable = 0;
    uid_data->logic_mode = 0;
  }

  if (!host_config.backend_shader_framebuffer_fetch)
  {
    if (uid_data->logic_mode == BlendMode::LogicOp::CLEAR ||
        uid_data->logic_mode == BlendMode::LogicOp::COPY_INVERTED ||
        uid_data->logic_mode == BlendMode::LogicOp::SET)
    {
      // handle these logic ops event backend doesn't support framebuffer fetch.
    }
    else
    {
      uid_data->logic_op_enable = 0;
      uid_data->logic_mode = 0;
    }
  }

  if (!g_ActiveConfig.backend_info.bSupportsEarlyZ)
  {
    uid_data->forced_early_z = 0;
  }

  // If bounding box is enabled when a UID cache is created, then later disabled, we shouldn't
  // emit the bounding box portion of the shader.
  uid_data->bounding_box &= host_config.bounding_box & host_config.backend_bbox;
}

void WritePixelShaderCommonHeader(ShaderCode& out, APIType ApiType, u32 num_texgens,
                                  const ShaderHostConfig& host_config, bool bounding_box)
{
  // dot product for integer vectors
  out.Write("int idot(int3 x, int3 y)\n"
            "{\n"
            "\tint3 tmp = x * y;\n"
            "\treturn tmp.x + tmp.y + tmp.z;\n"
            "}\n");

  out.Write("int idot(int4 x, int4 y)\n"
            "{\n"
            "\tint4 tmp = x * y;\n"
            "\treturn tmp.x + tmp.y + tmp.z + tmp.w;\n"
            "}\n\n");

  // rounding + casting to integer at once in a single function
  out.Write("int  iround(float  x) { return int (round(x)); }\n"
            "int2 iround(float2 x) { return int2(round(x)); }\n"
            "int3 iround(float3 x) { return int3(round(x)); }\n"
            "int4 iround(float4 x) { return int4(round(x)); }\n\n");

  if (ApiType == APIType::OpenGL || ApiType == APIType::Vulkan)
  {
    out.Write("SAMPLER_BINDING(0) uniform sampler2DArray samp[8];\n");
  }
  else  // D3D
  {
    // Declare samplers
    out.Write("SamplerState samp[8] : register(s0);\n");
    out.Write("\n");
    out.Write("Texture2DArray Tex[8] : register(t0);\n");
  }
  out.Write("\n");

  if (ApiType == APIType::OpenGL || ApiType == APIType::Vulkan)
    out.Write("UBO_BINDING(std140, 1) uniform PSBlock {\n");
  else
    out.Write("cbuffer PSBlock : register(b0) {\n");

  out.Write("\tint4 " I_COLORS "[4];\n"
            "\tint4 " I_KCOLORS "[4];\n"
            "\tint4 " I_ALPHA ";\n"
            "\tfloat4 " I_TEXDIMS "[8];\n"
            "\tint4 " I_ZBIAS "[2];\n"
            "\tint4 " I_INDTEXSCALE "[2];\n"
            "\tint4 " I_INDTEXMTX "[6];\n"
            "\tint4 " I_FOGCOLOR ";\n"
            "\tint4 " I_FOGI ";\n"
            "\tfloat4 " I_FOGF ";\n"
            "\tfloat4 " I_FOGRANGE "[3];\n"
            "\tfloat4 " I_ZSLOPE ";\n"
            "\tfloat2 " I_EFBSCALE ";\n"
            "};\n\n");

  if (host_config.per_pixel_lighting)
  {
    out.Write("%s", s_lighting_struct);

    if (ApiType == APIType::OpenGL || ApiType == APIType::Vulkan)
      out.Write("UBO_BINDING(std140, 2) uniform VSBlock {\n");
    else
      out.Write("cbuffer VSBlock : register(b1) {\n");

    out.Write(s_shader_uniforms);
    out.Write("};\n");
  }

  if (bounding_box)
  {
    out.Write(R"(
#ifdef API_D3D
globallycoherent RWBuffer<int> bbox_data : register(u2);
#define atomicMin InterlockedMin
#define atomicMax InterlockedMax
#define bbox_left bbox_data[0]
#define bbox_right bbox_data[1]
#define bbox_top bbox_data[2]
#define bbox_bottom bbox_data[3]
#else
SSBO_BINDING(0) buffer BBox {
  int bbox_left, bbox_right, bbox_top, bbox_bottom;
};
#endif

void UpdateBoundingBoxBuffer(int2 min_pos, int2 max_pos) {
  if (bbox_left > min_pos.x)
    atomicMin(bbox_left, min_pos.x);
  if (bbox_right < max_pos.x)
    atomicMax(bbox_right, max_pos.x);
  if (bbox_top > min_pos.y)
    atomicMin(bbox_top, min_pos.y);
  if (bbox_bottom < max_pos.y)
    atomicMax(bbox_bottom, max_pos.y);
}

void UpdateBoundingBox(float2 rawpos) {
  // The pixel center in the GameCube GPU is 7/12, not 0.5 (see VertexShaderGen.cpp)
  // Adjust for this by unapplying the offset we added in the vertex shader.
  const float PIXEL_CENTER_OFFSET = 7.0 / 12.0 - 0.5;
  float2 offset = float2(PIXEL_CENTER_OFFSET, %sPIXEL_CENTER_OFFSET);

  // The rightmost shaded pixel is not included in the right bounding box register,
  // such that width = right - left + 1. This has been verified on hardware.
  int2 pos = iround(rawpos * cefbscale + offset);

  // The GC/Wii GPU rasterizes in 2x2 pixel groups, so bounding box values will be rounded to the
  // extents of these groups, rather than the exact pixel.
#ifdef API_OPENGL
  // Need to flip the operands for Y on OpenGL because of lower-left origin.
  int2 pos_tl = int2(pos.x & ~1, pos.y | 1);
  int2 pos_br = int2(pos.x | 1, pos.y & ~1);
#else
  int2 pos_tl = pos & ~1;
  int2 pos_br = pos | 1;
#endif

#ifdef SUPPORTS_SUBGROUP_REDUCTION
  if (CAN_USE_SUBGROUP_REDUCTION) {
    int2 min_pos = IS_HELPER_INVOCATION ? int2(2147483647, 2147483647) : pos_tl;
    int2 max_pos = IS_HELPER_INVOCATION ? int2(-2147483648, -2147483648) : pos_br;
    SUBGROUP_MIN(min_pos);
    SUBGROUP_MAX(max_pos);
    if (IS_FIRST_ACTIVE_INVOCATION)
      UpdateBoundingBoxBuffer(min_pos, max_pos);
  } else {
    UpdateBoundingBoxBuffer(pos_tl, pos_br);
  }
#else
  UpdateBoundingBoxBuffer(pos_tl, pos_br);
#endif
}

)",
              ApiType == APIType::OpenGL ? "" : "-");
  }
}

static void WriteStage(ShaderCode& out, const pixel_shader_uid_data* uid_data, int n,
                       APIType ApiType);
static void WriteTevCombine(ShaderCode& out, const TevStageCombiner::ColorCombiner cc,
                            const TevStageCombiner::AlphaCombiner ac);
static void WriteTevRegular(ShaderCode& out, const char* components, int bias, int op, int shift);
static void SampleTexture(ShaderCode& out, const char* texcoords, const char* texswap, int texmap,
                          APIType ApiType);
static void WriteAlphaTest(ShaderCode& out, const pixel_shader_uid_data* uid_data, APIType ApiType,
                           bool per_pixel_depth, bool use_dual_source);
static void WriteFog(ShaderCode& out, const pixel_shader_uid_data* uid_data);
static void WriteColor(ShaderCode& out, APIType api_type, const pixel_shader_uid_data* uid_data,
                       bool use_dual_source);

static void WriteZCoord(ShaderCode& out, APIType api_type, const ShaderHostConfig& host_config,
                        const pixel_shader_uid_data* uid_data);

static void WriteLogicOp(ShaderCode& out, const pixel_shader_uid_data* uid_data);

ShaderCode GeneratePixelShaderCode(APIType ApiType, const ShaderHostConfig& host_config,
                                   const pixel_shader_uid_data* uid_data)
{
  ShaderCode out;

  const bool per_pixel_lighting = g_ActiveConfig.bEnablePixelLighting;
  const bool msaa = host_config.msaa;
  const bool ssaa = host_config.ssaa;
  const u32 numStages = uid_data->genMode_numtevstages + 1;

  out.Write("//Pixel Shader for TEV stages\n");
  out.Write("//%i TEV stages, %i texgens, %i IND stages\n", numStages, uid_data->genMode_numtexgens,
            uid_data->genMode_numindstages);

  // Stuff that is shared between ubershaders and pixelgen.
  WritePixelShaderCommonHeader(out, ApiType, uid_data->genMode_numtexgens, host_config,
                               uid_data->bounding_box);

  if (uid_data->forced_early_z)
  {
    // Zcomploc (aka early_ztest) is a way to control whether depth test is done before
    // or after texturing and alpha test. PC graphics APIs used to provide no way to emulate
    // this feature properly until 2012: Depth tests were always done after alpha testing.
    // Most importantly, it was not possible to write to the depth buffer without also writing
    // a color value (unless color writing was disabled altogether).

    // OpenGL 4.2 actually provides two extensions which can force an early z test:
    //  * ARB_image_load_store has 'layout(early_fragment_tests)' which forces the driver to do z
    //  and stencil tests early.
    //  * ARB_conservative_depth has 'layout(depth_unchanged) which signals to the driver that it
    //  can make optimisations
    //    which assume the pixel shader won't update the depth buffer.

    // early_fragment_tests is the best option, as it requires the driver to do early-z and defines
    // early-z exactly as
    // we expect, with discard causing the shader to exit with only the depth buffer updated.

    // Conservative depth's 'depth_unchanged' only hints to the driver that an early-z optimisation
    // can be made and
    // doesn't define what will happen if we discard the fragment. But the way modern graphics
    // hardware is implemented
    // means it is not unreasonable to expect the same behaviour as early_fragment_tests.
    // We can also assume that if a driver has gone out of its way to support conservative depth and
    // not image_load_store
    // as required by OpenGL 4.2 that it will be doing the optimisation.
    // If the driver doesn't actually do an early z optimisation, ZCompLoc will be broken and depth
    // will only be written
    // if the alpha test passes.

    // We support Conservative as a fallback, because many drivers based on Mesa haven't implemented
    // all of the
    // ARB_image_load_store extension yet.

    // D3D11 also has a way to force the driver to enable early-z, so we're fine here.
    if (ApiType == APIType::OpenGL || ApiType == APIType::Vulkan)
    {
      // This is a #define which signals whatever early-z method the driver supports.
      out.Write("FORCE_EARLY_Z; \n");
    }
    else
    {
      out.Write("[earlydepthstencil]\n");
    }
  }

  // Only use dual-source blending when required on drivers that don't support it very well.
  bool use_dual_source = uid_data->dualSrcBlend;
  bool use_shader_logic = uid_data->logic_op_enable;

  if (ApiType == APIType::OpenGL || ApiType == APIType::Vulkan)
  {
    if (use_dual_source)
    {
      char const* output_location0;
      char const* output_location1;
      if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_FRAGMENT_SHADER_INDEX_DECORATION))
      {
        output_location0 = "FRAGMENT_OUTPUT_LOCATION(0)";
        output_location1 = "FRAGMENT_OUTPUT_LOCATION(1)";
      }
      else
      {
        output_location0 = "FRAGMENT_OUTPUT_LOCATION_INDEXED(0, 0)";
        output_location1 = "FRAGMENT_OUTPUT_LOCATION_INDEXED(0, 1)";
      }

      if (use_shader_logic)
      {
        if (host_config.backend_shader_framebuffer_fetch)
        {
          out.Write("%s FRAGMENT_INOUT vec4 real_ocol0;\n", output_location0);
        }
        else
        {
          out.Write("%s out vec4 real_ocol0;\n", output_location0);
        }
      }
      else
      {
        out.Write("%s out vec4 ocol0;\n", output_location0);
      }

      // dual src output1
      out.Write("%s out vec4 ocol1;\n", output_location1);
    }
    else if (use_shader_logic)
    {
      if (host_config.backend_shader_framebuffer_fetch)
      {
        out.Write("FRAGMENT_OUTPUT_LOCATION(0) FRAGMENT_INOUT vec4 real_ocol0;\n");
      }
      else
      {
        out.Write("FRAGMENT_OUTPUT_LOCATION(0) out vec4 real_ocol0;\n");
      }
    }
    else
    {
      out.Write("FRAGMENT_OUTPUT_LOCATION(0) out vec4 ocol0;\n");
    }

    if (uid_data->per_pixel_depth)
      out.Write("#define depth gl_FragDepth\n");

    if (host_config.backend_geometry_shaders)
    {
      out.Write("VARYING_LOCATION(0) in VertexData {\n");
      GenerateVSOutputMembers(out, ApiType, uid_data->genMode_numtexgens, host_config,
                              GetInterpolationQualifier(msaa, ssaa, true, true));

      out.Write("};\n");
    }
    else
    {
      // Let's set up attributes
      u32 counter = 0;
      out.Write("VARYING_LOCATION(%u) %s in float4 colors_0;\n", counter++,
                GetInterpolationQualifier(msaa, ssaa));
      out.Write("VARYING_LOCATION(%u) %s in float4 colors_1;\n", counter++,
                GetInterpolationQualifier(msaa, ssaa));
      for (unsigned int i = 0; i < uid_data->genMode_numtexgens; ++i)
      {
        out.Write("VARYING_LOCATION(%u) %s in float3 tex%d;\n", counter++,
                  GetInterpolationQualifier(msaa, ssaa), i);
      }
      if (!host_config.fast_depth_calc)
        out.Write("VARYING_LOCATION(%u) %s in float4 clipPos;\n", counter++,
                  GetInterpolationQualifier(msaa, ssaa));
      if (per_pixel_lighting)
      {
        out.Write("VARYING_LOCATION(%u) %s in float3 Normal;\n", counter++,
                  GetInterpolationQualifier(msaa, ssaa));
        out.Write("VARYING_LOCATION(%u) %s in float3 WorldPos;\n", counter++,
                  GetInterpolationQualifier(msaa, ssaa));
      }
    }

    out.Write("void main()\n{\n");
    out.Write("\tfloat4 rawpos = gl_FragCoord;\n");
    if (use_shader_logic)
    {
      if (host_config.backend_shader_framebuffer_fetch)
      {
        out.Write("\tfloat4 initial_ocol0 = FB_FETCH_VALUE;\n");
      }
      else
      {
        out.Write("\tfloat4 initial_ocol0 = float4(0.0f, 0.0f, 0.0f, 0.0f);\n");
      }
      out.Write("\tfloat4 ocol0;\n");
    }
  }
  else  // D3D
  {
    out.Write("void main(\n");
    if (uid_data->uint_output)
    {
      out.Write("  out uint4 ocol0 : SV_Target,\n");
    }
    else
    {
      out.Write("  out float4 ocol0 : SV_Target0,\n"
                "  out float4 ocol1 : SV_Target1,\n");
    }
    out.Write("%s"
              "  in float4 rawpos : SV_Position,\n",
              uid_data->per_pixel_depth ? "  out float depth : SV_Depth,\n" : "");

    out.Write("  in %s float4 colors_0 : COLOR0,\n", GetInterpolationQualifier(msaa, ssaa));
    out.Write("  in %s float4 colors_1 : COLOR1\n", GetInterpolationQualifier(msaa, ssaa));

    // compute window position if needed because binding semantic WPOS is not widely supported
    for (unsigned int i = 0; i < uid_data->genMode_numtexgens; ++i)
    {
      out.Write(",\n  in %s float3 tex%d : TEXCOORD%d", GetInterpolationQualifier(msaa, ssaa), i,
                i);
    }
    if (!host_config.fast_depth_calc)
    {
      out.Write(",\n  in %s float4 clipPos : TEXCOORD%d", GetInterpolationQualifier(msaa, ssaa),
                uid_data->genMode_numtexgens);
    }
    if (per_pixel_lighting)
    {
      out.Write(",\n  in %s float3 Normal : TEXCOORD%d", GetInterpolationQualifier(msaa, ssaa),
                uid_data->genMode_numtexgens + 1);
      out.Write(",\n  in %s float3 WorldPos : TEXCOORD%d", GetInterpolationQualifier(msaa, ssaa),
                uid_data->genMode_numtexgens + 2);
    }
    if (host_config.backend_geometry_shaders)
    {
      out.Write(",\n  in float clipDist0 : SV_ClipDistance0\n");
      out.Write(",\n  in float clipDist1 : SV_ClipDistance1\n");
    }
    out.Write("        ) {\n");
  }

  out.Write("\tint4 c0 = " I_COLORS "[1], c1 = " I_COLORS "[2], c2 = " I_COLORS
            "[3], prev = " I_COLORS "[0];\n"
            "\tint4 rastemp, textemp, konsttemp;\n"
            "\tint3 comp16 = int3(1, 256, 0), comp24 = int3(1, 256, 256*256);\n"
            "\tint alphabump = 0;\n"
            "\tint2 tevcoord = int2(0, 0);\n"
            "\tint2 wrappedcoord, tempcoord;\n"
            "\tint4 tevin_a, tevin_b, tevin_c, tevin_d, tevin_temp;\n\n");  // tev combiner inputs

  // On GLSL, input variables must not be assigned to.
  // This is why we declare these variables locally instead.
  out.Write("\tfloat4 col0 = colors_0;\n");
  out.Write("\tfloat4 col1 = colors_1;\n");

  if (per_pixel_lighting)
  {
    out.Write("\tfloat3 _norm0 = normalize(Normal.xyz);\n\n");
    out.Write("\tfloat3 pos = WorldPos;\n");

    out.Write("\tint4 lacc;\n"
              "\tfloat3 ldir, h, cosAttn, distAttn;\n"
              "\tfloat dist, dist2, attn;\n");

    // TODO: Our current constant usage code isn't able to handle more than one buffer.
    //       So we can't mark the VS constant as used here. But keep them here as reference.
    // out.SetConstantsUsed(C_PLIGHT_COLORS, C_PLIGHT_COLORS+7); // TODO: Can be optimized further
    // out.SetConstantsUsed(C_PLIGHTS, C_PLIGHTS+31); // TODO: Can be optimized further
    // out.SetConstantsUsed(C_PMATERIALS, C_PMATERIALS+3);
    GenerateLightingShaderCode(out, uid_data->lighting, uid_data->components << VB_COL_SHIFT,
                               "colors_", "col");
  }

  // HACK to handle cases where the tex gen is not enabled
  if (uid_data->genMode_numtexgens == 0)
  {
    out.Write("\tint2 fixpoint_uv0 = int2(0, 0);\n\n");
  }
  else
  {
    out.SetConstantsUsed(C_TEXDIMS, C_TEXDIMS + uid_data->genMode_numtexgens - 1);
    for (unsigned int i = 0; i < uid_data->genMode_numtexgens; ++i)
    {
      out.Write("\tint2 fixpoint_uv%d = int2(", i);
      out.Write("(tex%d.z == 0.0 ? tex%d.xy : tex%d.xy / tex%d.z)", i, i, i, i);
      out.Write(" * " I_TEXDIMS "[%d].zw);\n", i);
      // TODO: S24 overflows here?
    }
  }

  for (u32 i = 0; i < uid_data->genMode_numindstages; ++i)
  {
    if (uid_data->nIndirectStagesUsed & (1 << i))
    {
      unsigned int texcoord = uid_data->GetTevindirefCoord(i);
      unsigned int texmap = uid_data->GetTevindirefMap(i);

      if (texcoord < uid_data->genMode_numtexgens)
      {
        out.SetConstantsUsed(C_INDTEXSCALE + i / 2, C_INDTEXSCALE + i / 2);
        out.Write("\ttempcoord = fixpoint_uv%d >> " I_INDTEXSCALE "[%d].%s;\n", texcoord, i / 2,
                  (i & 1) ? "zw" : "xy");
      }
      else
      {
        out.Write("\ttempcoord = int2(0, 0);\n");
      }

      out.Write("\tint3 iindtex%d = ", i);
      SampleTexture(out, "float2(tempcoord)", "abg", texmap, ApiType);
    }
  }

  for (u32 i = 0; i < numStages; i++)
  {
    // Build the equation for this stage
    WriteStage(out, uid_data, i, ApiType);
  }

  {
    // The results of the last texenv stage are put onto the screen,
    // regardless of the used destination register
    TevStageCombiner::ColorCombiner last_cc;
    TevStageCombiner::AlphaCombiner last_ac;
    last_cc.hex = uid_data->stagehash[uid_data->genMode_numtevstages].cc;
    last_ac.hex = uid_data->stagehash[uid_data->genMode_numtevstages].ac;
    if (last_cc.hex == last_ac.hex)
    {
      if (last_cc.dest != 0)
      {
        out.Write("\tprev = %s;\n", tev_output_table[last_cc.dest]);
      }
    }
    else
    {
      if (last_cc.dest != 0)
      {
        out.Write("\tprev.rgb = %s;\n", tev_c_output_table[last_cc.dest]);
      }

      if (last_ac.dest != 0)
      {
        out.Write("\tprev.a = %s;\n", tev_a_output_table[last_ac.dest]);
      }
    }
  }
  out.Write("\tprev = prev & 255;\n");

  // NOTE: Fragment may not be discarded if alpha test always fails and early depth test is enabled
  // (in this case we need to write a depth value if depth test passes regardless of the alpha
  // testing result)
  if (uid_data->Pretest == AlphaTest::UNDETERMINED ||
      (uid_data->Pretest == AlphaTest::FAIL && uid_data->late_ztest))
  {
    WriteAlphaTest(out, uid_data, ApiType, uid_data->per_pixel_depth, use_dual_source);
  }

  // write zcoord
  bool need_write_zcoord = true;

  // depth texture can safely be ignored if the result won't be written to the depth buffer
  // (early_ztest) and isn't used for fog either
  const bool skip_ztexture = !uid_data->per_pixel_depth && !uid_data->fog_fsel;

  // Note: z-textures are not written to depth buffer if early depth test is used
  if (uid_data->per_pixel_depth && uid_data->early_ztest)
  {
    if (need_write_zcoord)
    {
      WriteZCoord(out, ApiType, host_config, uid_data);
      need_write_zcoord = false;
    }
    if (!host_config.backend_reversed_depth_range)
      out.Write("\tdepth = 1.0 - float(zCoord) / 16777216.0;\n");
    else
      out.Write("\tdepth = float(zCoord) / 16777216.0;\n");
  }

  // Note: depth texture output is only written to depth buffer if late depth test is used
  // theoretical final depth value is used for fog calculation, though, so we have to emulate
  // ztextures anyway
  if (uid_data->ztex_op != ZTEXTURE_DISABLE && !skip_ztexture)
  {
    if (need_write_zcoord)
    {
      WriteZCoord(out, ApiType, host_config, uid_data);
      need_write_zcoord = false;
    }

    // use the texture input of the last texture stage (textemp), hopefully this has been read and
    // is in correct format...
    out.SetConstantsUsed(C_ZBIAS, C_ZBIAS + 1);
    out.Write("\tzCoord = idot(" I_ZBIAS "[0].xyzw, textemp.xyzw) + " I_ZBIAS "[1].w %s;\n",
              (uid_data->ztex_op == ZTEXTURE_ADD) ? "+ zCoord" : "");
    out.Write("\tzCoord = zCoord & 0xFFFFFF;\n");
  }

  if (uid_data->per_pixel_depth && uid_data->late_ztest)
  {
    if (need_write_zcoord)
    {
      WriteZCoord(out, ApiType, host_config, uid_data);
      need_write_zcoord = false;
    }
    if (!host_config.backend_reversed_depth_range)
      out.Write("\tdepth = 1.0 - float(zCoord) / 16777216.0;\n");
    else
      out.Write("\tdepth = float(zCoord) / 16777216.0;\n");
  }

  // No dithering for RGB8 mode
  if (uid_data->dither)
  {
    // Flipper uses a standard 2x2 Bayer Matrix for 6 bit dithering
    // Here the matrix is encoded into the two factor constants
    out.Write("\tint2 dither = int2(rawpos.xy) & 1;\n");
    out.Write("\tprev.rgb = (prev.rgb - (prev.rgb >> 6)) + abs(dither.y * 3 - dither.x * 2);\n");
  }

  if (uid_data->fog_fsel != 0)
  {
    if (need_write_zcoord)
    {
      WriteZCoord(out, ApiType, host_config, uid_data);
      need_write_zcoord = false;
    }

    WriteFog(out, uid_data);
  }

  // Write the color and alpha values to the framebuffer
  // If using shader blend, we still use the separate alpha
  WriteColor(out, ApiType, uid_data, use_dual_source);

  if (use_shader_logic)
    WriteLogicOp(out, uid_data);

  if (uid_data->bounding_box)
    out.Write("\tUpdateBoundingBox(rawpos.xy);\n");

  out.Write("}\n");
  return out;
}

static void WriteStage(ShaderCode& out, const pixel_shader_uid_data* uid_data, int n,
                       APIType ApiType)
{
  auto& stage = uid_data->stagehash[n];
  out.Write("\n\t// TEV stage %d\n", n);

  // HACK to handle cases where the tex gen is not enabled
  u32 texcoord = stage.tevorders_texcoord;
  bool bHasTexCoord = texcoord < uid_data->genMode_numtexgens;
  if (!bHasTexCoord)
    texcoord = 0;

  if (stage.hasindstage)
  {
    TevStageIndirect tevind;
    tevind.hex = stage.tevind;

    out.Write("\t// indirect op\n");
    // perform the indirect op on the incoming regular coordinates using iindtex%d as the offset
    // coords
    if (tevind.bs != ITBA_OFF)
    {
      constexpr std::array<const char*, 4> tev_ind_alpha_sel{
          "",
          "x",
          "y",
          "z",
      };

      // 0b11111000, 0b11100000, 0b11110000, 0b11111000
      constexpr std::array<const char*, 4> tev_ind_alpha_mask{
          "248",
          "224",
          "240",
          "248",
      };

      out.Write("alphabump = iindtex%d.%s & %s;\n", tevind.bt.Value(), tev_ind_alpha_sel[tevind.bs],
                tev_ind_alpha_mask[tevind.fmt]);
    }
    else
    {
      // TODO: Should we reset alphabump to 0 here?
    }

    if (tevind.mid != 0)
    {
      // format
      constexpr std::array<const char*, 4> tev_ind_fmt_mask{
          "255",
          "31",
          "15",
          "7",
      };
      out.Write("\tint3 iindtevcrd%d = iindtex%d & %s;\n", n, tevind.bt.Value(),
                tev_ind_fmt_mask[tevind.fmt]);

      // bias - TODO: Check if this needs to be this complicated...
      // indexed by bias
      constexpr std::array<const char*, 8> tev_ind_bias_field{
          "", "x", "y", "xy", "z", "xz", "yz", "xyz",
      };

      // indexed by fmt
      constexpr std::array<const char*, 4> tev_ind_bias_add{
          "-128",
          "1",
          "1",
          "1",
      };

      if (tevind.bias == ITB_S || tevind.bias == ITB_T || tevind.bias == ITB_U)
      {
        out.Write("\tiindtevcrd%d.%s += int(%s);\n", n, tev_ind_bias_field[tevind.bias],
                  tev_ind_bias_add[tevind.fmt]);
      }
      else if (tevind.bias == ITB_ST || tevind.bias == ITB_SU || tevind.bias == ITB_TU)
      {
        out.Write("\tiindtevcrd%d.%s += int2(%s, %s);\n", n, tev_ind_bias_field[tevind.bias],
                  tev_ind_bias_add[tevind.fmt], tev_ind_bias_add[tevind.fmt]);
      }
      else if (tevind.bias == ITB_STU)
      {
        out.Write("\tiindtevcrd%d.%s += int3(%s, %s, %s);\n", n, tev_ind_bias_field[tevind.bias],
                  tev_ind_bias_add[tevind.fmt], tev_ind_bias_add[tevind.fmt],
                  tev_ind_bias_add[tevind.fmt]);
      }

      // multiply by offset matrix and scale - calculations are likely to overflow badly,
      // yet it works out since we only care about the lower 23 bits (+1 sign bit) of the result
      if (tevind.mid <= 3)
      {
        int mtxidx = 2 * (tevind.mid - 1);
        out.SetConstantsUsed(C_INDTEXMTX + mtxidx, C_INDTEXMTX + mtxidx);

        out.Write("\tint2 indtevtrans%d = int2(idot(" I_INDTEXMTX
                  "[%d].xyz, iindtevcrd%d), idot(" I_INDTEXMTX "[%d].xyz, iindtevcrd%d)) >> 3;\n",
                  n, mtxidx, n, mtxidx + 1, n);

        // TODO: should use a shader uid branch for this for better performance
        if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_BITWISE_OP_NEGATION))
        {
          out.Write("\tint indtexmtx_w_inverse_%d = -" I_INDTEXMTX "[%d].w;\n", n, mtxidx);
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= indtexmtx_w_inverse_%d;\n", n, n);
        }
        else
        {
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= (-" I_INDTEXMTX "[%d].w);\n", n, mtxidx);
        }
      }
      else if (tevind.mid <= 7 && bHasTexCoord)
      {  // s matrix
        ASSERT(tevind.mid >= 5);
        int mtxidx = 2 * (tevind.mid - 5);
        out.SetConstantsUsed(C_INDTEXMTX + mtxidx, C_INDTEXMTX + mtxidx);

        out.Write("\tint2 indtevtrans%d = int2(fixpoint_uv%d * iindtevcrd%d.xx) >> 8;\n", n,
                  texcoord, n);
        if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_BITWISE_OP_NEGATION))
        {
          out.Write("\tint  indtexmtx_w_inverse_%d = -" I_INDTEXMTX "[%d].w;\n", n, mtxidx);
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= (indtexmtx_w_inverse_%d);\n", n, n);
        }
        else
        {
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= (-" I_INDTEXMTX "[%d].w);\n", n, mtxidx);
        }
      }
      else if (tevind.mid <= 11 && bHasTexCoord)
      {  // t matrix
        ASSERT(tevind.mid >= 9);
        int mtxidx = 2 * (tevind.mid - 9);
        out.SetConstantsUsed(C_INDTEXMTX + mtxidx, C_INDTEXMTX + mtxidx);

        out.Write("\tint2 indtevtrans%d = int2(fixpoint_uv%d * iindtevcrd%d.yy) >> 8;\n", n,
                  texcoord, n);

        if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_BITWISE_OP_NEGATION))
        {
          out.Write("\tint  indtexmtx_w_inverse_%d = -" I_INDTEXMTX "[%d].w;\n", n, mtxidx);
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= (indtexmtx_w_inverse_%d);\n", n, n);
        }
        else
        {
          out.Write("\tif (" I_INDTEXMTX "[%d].w >= 0) indtevtrans%d >>= " I_INDTEXMTX "[%d].w;\n",
                    mtxidx, n, mtxidx);
          out.Write("\telse indtevtrans%d <<= (-" I_INDTEXMTX "[%d].w);\n", n, mtxidx);
        }
      }
      else
      {
        out.Write("\tint2 indtevtrans%d = int2(0, 0);\n", n);
      }
    }
    else
    {
      out.Write("\tint2 indtevtrans%d = int2(0, 0);\n", n);
    }

    // ---------
    // Wrapping
    // ---------
    // TODO: Should the last element be 1 or (1<<7)?
    constexpr std::array<const char*, 7> tev_ind_wrap_start{
        "0", "(256<<7)", "(128<<7)", "(64<<7)", "(32<<7)", "(16<<7)", "1",
    };

    // wrap S
    if (tevind.sw == ITW_OFF)
    {
      out.Write("\twrappedcoord.x = fixpoint_uv%d.x;\n", texcoord);
    }
    else if (tevind.sw == ITW_0)
    {
      out.Write("\twrappedcoord.x = 0;\n");
    }
    else
    {
      out.Write("\twrappedcoord.x = fixpoint_uv%d.x & (%s - 1);\n", texcoord,
                tev_ind_wrap_start[tevind.sw]);
    }

    // wrap T
    if (tevind.tw == ITW_OFF)
    {
      out.Write("\twrappedcoord.y = fixpoint_uv%d.y;\n", texcoord);
    }
    else if (tevind.tw == ITW_0)
    {
      out.Write("\twrappedcoord.y = 0;\n");
    }
    else
    {
      out.Write("\twrappedcoord.y = fixpoint_uv%d.y & (%s - 1);\n", texcoord,
                tev_ind_wrap_start[tevind.tw]);
    }

    if (tevind.fb_addprev)  // add previous tevcoord
      out.Write("\ttevcoord += wrappedcoord + indtevtrans%d;\n", n);
    else
      out.Write("\ttevcoord = wrappedcoord + indtevtrans%d;\n", n);

    // Emulate s24 overflows
    out.Write("\ttevcoord = (tevcoord << 8) >> 8;\n");
  }

  TevStageCombiner::ColorCombiner cc;
  TevStageCombiner::AlphaCombiner ac;
  cc.hex = stage.cc;
  ac.hex = stage.ac;

  if (cc.a == TEVCOLORARG_RASA || cc.a == TEVCOLORARG_RASC || cc.b == TEVCOLORARG_RASA ||
      cc.b == TEVCOLORARG_RASC || cc.c == TEVCOLORARG_RASA || cc.c == TEVCOLORARG_RASC ||
      cc.d == TEVCOLORARG_RASA || cc.d == TEVCOLORARG_RASC || ac.a == TEVALPHAARG_RASA ||
      ac.b == TEVALPHAARG_RASA || ac.c == TEVALPHAARG_RASA || ac.d == TEVALPHAARG_RASA)
  {
    // Generate swizzle string to represent the Ras color channel swapping
    const char rasswap[5] = {
        "rgba"[stage.tevksel_swap1a],
        "rgba"[stage.tevksel_swap2a],
        "rgba"[stage.tevksel_swap1b],
        "rgba"[stage.tevksel_swap2b],
        '\0',
    };

    out.Write("\trastemp = %s.%s;\n", tev_ras_table[stage.tevorders_colorchan], rasswap);
  }

  if (stage.tevorders_enable)
  {
    // Generate swizzle string to represent the texture color channel swapping
    const char texswap[5] = {
        "rgba"[stage.tevksel_swap1c],
        "rgba"[stage.tevksel_swap2c],
        "rgba"[stage.tevksel_swap1d],
        "rgba"[stage.tevksel_swap2d],
        '\0',
    };

    if (!stage.hasindstage)
    {
      // calc tevcord
      if (bHasTexCoord)
        out.Write("\ttevcoord = fixpoint_uv%d;\n", texcoord);
      else
        out.Write("\ttevcoord = int2(0, 0);\n");
    }
    out.Write("\ttextemp = ");
    SampleTexture(out, "float2(tevcoord)", texswap, stage.tevorders_texmap, ApiType);
  }
  else
  {
    out.Write("\ttextemp = int4(255, 255, 255, 255);\n");
  }

  if (cc.a == TEVCOLORARG_KONST || cc.b == TEVCOLORARG_KONST || cc.c == TEVCOLORARG_KONST ||
      cc.d == TEVCOLORARG_KONST || ac.a == TEVALPHAARG_KONST || ac.b == TEVALPHAARG_KONST ||
      ac.c == TEVALPHAARG_KONST || ac.d == TEVALPHAARG_KONST)
  {
    out.Write("\tkonsttemp = int4(%s, %s);\n", tev_ksel_table_c[stage.tevksel_kc],
              tev_ksel_table_a[stage.tevksel_ka]);

    if (stage.tevksel_kc > 7)
    {
      out.SetConstantsUsed(C_KCOLORS + ((stage.tevksel_kc - 0xc) % 4),
                           C_KCOLORS + ((stage.tevksel_kc - 0xc) % 4));
    }
    if (stage.tevksel_ka > 7)
    {
      out.SetConstantsUsed(C_KCOLORS + ((stage.tevksel_ka - 0xc) % 4),
                           C_KCOLORS + ((stage.tevksel_ka - 0xc) % 4));
    }
  }

  if (cc.d == TEVCOLORARG_C0 || cc.d == TEVCOLORARG_A0 || ac.d == TEVALPHAARG_A0)
    out.SetConstantsUsed(C_COLORS + 1, C_COLORS + 1);

  if (cc.d == TEVCOLORARG_C1 || cc.d == TEVCOLORARG_A1 || ac.d == TEVALPHAARG_A1)
    out.SetConstantsUsed(C_COLORS + 2, C_COLORS + 2);

  if (cc.d == TEVCOLORARG_C2 || cc.d == TEVCOLORARG_A2 || ac.d == TEVALPHAARG_A2)
    out.SetConstantsUsed(C_COLORS + 3, C_COLORS + 3);

  if (cc.dest >= GX_TEVREG0)
    out.SetConstantsUsed(C_COLORS + cc.dest, C_COLORS + cc.dest);

  if (ac.dest >= GX_TEVREG0)
    out.SetConstantsUsed(C_COLORS + ac.dest, C_COLORS + ac.dest);

  if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_VECTOR_BITWISE_AND))
  {
    out.Write("\ttevin_a = int4(%s & 255, %s & 255);\n", tev_c_input_table[cc.a], tev_a_input_table[ac.a]);
    out.Write("\ttevin_b = int4(%s & 255, %s & 255);\n", tev_c_input_table[cc.b], tev_a_input_table[ac.b]);
    out.Write("\ttevin_c = int4(%s & 255, %s & 255);\n", tev_c_input_table[cc.c], tev_a_input_table[ac.c]);
  }
  else
  {
    out.Write("\ttevin_a = int4(%s, %s) & 255;\n", tev_c_input_table[cc.a], tev_a_input_table[ac.a]);
    out.Write("\ttevin_b = int4(%s, %s) & 255;\n", tev_c_input_table[cc.b], tev_a_input_table[ac.b]);
    out.Write("\ttevin_c = int4(%s, %s) & 255;\n", tev_c_input_table[cc.c], tev_a_input_table[ac.c]);
  }
  out.Write("\ttevin_d = int4(%s, %s);\n", tev_c_input_table[cc.d], tev_a_input_table[ac.d]);

  if (cc.bias != TEVBIAS_COMPARE || ac.bias != TEVBIAS_COMPARE)
  {
    out.Write("\ttevin_temp = (tevin_a<<8) + (tevin_b-tevin_a) * (tevin_c + (tevin_c>>7));\n");
  }

  WriteTevCombine(out, cc, ac);
}

static void WriteTevCombine(ShaderCode& out, const TevStageCombiner::ColorCombiner cc,
                            const TevStageCombiner::AlphaCombiner ac)
{
  constexpr std::array<const char*, 8> all_function_table{
      "((tevin_a.r > tevin_b.r) ? tevin_c : int4(0, 0, 0, 0))",   // TEVCMP_R8_GT
      "((tevin_a.r == tevin_b.r) ? tevin_c : int4(0, 0, 0, 0))",  // TEVCMP_R8_EQ
      "((idot(tevin_a.rgb, comp16) >  idot(tevin_b.rgb, comp16)) ? tevin_c : int4(0, 0, 0, "
      "0))",  // TEVCMP_GR16_GT
      "((idot(tevin_a.rgb, comp16) == idot(tevin_b.rgb, comp16)) ? tevin_c : int4(0, 0, 0, "
      "0))",  // TEVCMP_GR16_EQ
      "((idot(tevin_a.rgb, comp24) >  idot(tevin_b.rgb, comp24)) ? tevin_c : int4(0, 0, 0, "
      "0))",  // TEVCMP_BGR24_GT
      "((idot(tevin_a.rgb, comp24) == idot(tevin_b.rgb, comp24)) ? tevin_c : int4(0, 0, 0, "
      "0))",      // TEVCMP_BGR24_EQ
      "ERROR#6",  // TEVCMP_RGB8_GT or TEVCMP_A8_GT
      "ERROR#7"   // TEVCMP_RGB8_EQ or TEVCMP_A8_EQ
  };

  constexpr std::array<const char*, 8> color_function_table{
      "((tevin_a.r > tevin_b.r) ? tevin_c.rgb : int3(0, 0, 0))",   // TEVCMP_R8_GT
      "((tevin_a.r == tevin_b.r) ? tevin_c.rgb : int3(0, 0, 0))",  // TEVCMP_R8_EQ
      "((idot(tevin_a.rgb, comp16) >  idot(tevin_b.rgb, comp16)) ? tevin_c.rgb : int3(0, 0, "
      "0))",  // TEVCMP_GR16_GT
      "((idot(tevin_a.rgb, comp16) == idot(tevin_b.rgb, comp16)) ? tevin_c.rgb : int3(0, 0, "
      "0))",  // TEVCMP_GR16_EQ
      "((idot(tevin_a.rgb, comp24) >  idot(tevin_b.rgb, comp24)) ? tevin_c.rgb : int3(0, 0, "
      "0))",  // TEVCMP_BGR24_GT
      "((idot(tevin_a.rgb, comp24) == idot(tevin_b.rgb, comp24)) ? tevin_c.rgb : int3(0, 0, "
      "0))",                                                                    // TEVCMP_BGR24_EQ
      "(max(sign(tevin_a.rgb - tevin_b.rgb), int3(0, 0, 0)) * tevin_c.rgb)",    // TEVCMP_RGB8_GT
      "((int3(1, 1, 1) - sign(abs(tevin_a.rgb - tevin_b.rgb))) * tevin_c.rgb)"  // TEVCMP_RGB8_EQ
  };

  constexpr std::array<const char*, 8> alpha_function_table{
      "((tevin_a.r > tevin_b.r) ? tevin_c.a : 0)",                                   // TEVCMP_R8_GT
      "((tevin_a.r == tevin_b.r) ? tevin_c.a : 0)",                                  // TEVCMP_R8_EQ
      "((idot(tevin_a.rgb, comp16) >  idot(tevin_b.rgb, comp16)) ? tevin_c.a : 0)",  // TEVCMP_GR16_GT
      "((idot(tevin_a.rgb, comp16) == idot(tevin_b.rgb, comp16)) ? tevin_c.a : 0)",  // TEVCMP_GR16_EQ
      "((idot(tevin_a.rgb, comp24) >  idot(tevin_b.rgb, comp24)) ? tevin_c.a : 0)",  // TEVCMP_BGR24_GT
      "((idot(tevin_a.rgb, comp24) == idot(tevin_b.rgb, comp24)) ? tevin_c.a : 0)",  // TEVCMP_BGR24_EQ
      "((tevin_a.a >  tevin_b.a) ? tevin_c.a : 0)",                                  // TEVCMP_A8_GT
      "((tevin_a.a == tevin_b.a) ? tevin_c.a : 0)"                                   // TEVCMP_A8_EQ
  };

  int mode = (cc.shift << 1) | cc.op;

  if (cc.dest == ac.dest && cc.bias == ac.bias && cc.op == ac.op && cc.shift == ac.shift)
  {
    out.Write("\t// tev combine\n");
    if (cc.bias != TEVBIAS_COMPARE)
    {
      out.Write("\t%s = ", tev_output_table[cc.dest]);
      WriteTevRegular(out, "rgba", cc.bias, cc.op, cc.shift);
    }
    else
    {
      if (mode == 6 || mode == 7)
      {
        out.Write("\t%s = tevin_d.rgb + %s;\n", tev_c_output_table[cc.dest],
                  color_function_table[mode]);
        out.Write("\t%s = tevin_d.a + %s;\n", tev_a_output_table[cc.dest], alpha_function_table[mode]);
      }
      else
      {
        out.Write("\t%s = tevin_d + %s;\n", tev_output_table[cc.dest], all_function_table[mode]);
      }
    }
  }
  else
  {
    out.Write("\t// color combine\n");
    if (cc.bias != TEVBIAS_COMPARE)
    {
      out.Write("\t%s = ", tev_c_output_table[cc.dest]);
      WriteTevRegular(out, "rgb", cc.bias, cc.op, cc.shift);
    }
    else
    {
      out.Write("\t%s = tevin_d.rgb + %s;\n", tev_c_output_table[cc.dest], color_function_table[mode]);
    }

    out.Write("\t// alpha combine\n");
    mode = (ac.shift << 1) | ac.op;
    if (ac.bias != TEVBIAS_COMPARE)
    {
      out.Write("\t%s = ", tev_a_output_table[ac.dest]);
      WriteTevRegular(out, "a", ac.bias, ac.op, ac.shift);
    }
    else
    {
      out.Write("\t%s = tevin_d.a + %s;\n", tev_a_output_table[ac.dest], alpha_function_table[mode]);
    }
  }

  // clamp
  if (cc.dest == ac.dest && cc.clamp == ac.clamp)
  {
    // color + alpha
    if (cc.clamp)
    {
      out.Write("\t%s = clamp(%s, int4(0, 0, 0, 0), int4(255, 255, 255, 255));\n",
                tev_output_table[cc.dest], tev_output_table[cc.dest]);
    }
    else
    {
      out.Write(
          "\t%s = clamp(%s, int4(-1024, -1024, -1024, -1024), int4(1023, 1023, 1023, 1023));\n",
          tev_output_table[cc.dest], tev_output_table[cc.dest]);
    }
  }
  else
  {
    // color
    if (cc.clamp)
    {
      out.Write("\t%s = clamp(%s, int3(0, 0, 0), int3(255, 255, 255));\n", tev_c_output_table[cc.dest],
                tev_c_output_table[cc.dest]);
    }
    else
    {
      out.Write("\t%s = clamp(%s, int3(-1024, -1024, -1024), int3(1023, 1023, 1023));\n",
                tev_c_output_table[cc.dest], tev_c_output_table[cc.dest]);
    }

    // alpha
    if (ac.clamp)
    {
      out.Write("\t%s = clamp(%s, 0, 255);\n", tev_a_output_table[ac.dest], tev_a_output_table[ac.dest]);
    }
    else
    {
      out.Write("\t%s = clamp(%s, -1024, 1023);\n", tev_a_output_table[ac.dest],
                tev_a_output_table[ac.dest]);
    }
  }
}

static void WriteTevRegular(ShaderCode& out, const char* components, int bias, int op, int shift)
{
  constexpr std::array<const char*, 4> tev_scale_table_left{
      "",       // SCALE_1
      " << 1",  // SCALE_2
      " << 2",  // SCALE_4
      "",       // DIVIDE_2
  };

  constexpr std::array<const char*, 4> tev_scale_table_right{
      "",       // SCALE_1
      "",       // SCALE_2
      "",       // SCALE_4
      " >> 1",  // DIVIDE_2
  };

  constexpr std::array<const char*, 2> tev_lerp_bias{
      " + 128",
      " + 127",
  };

  constexpr std::array<const char*, 4> tev_bias_table{
      "",        // ZERO,
      " + 128",  // ADDHALF,
      " - 128",  // SUBHALF,
      "",
  };

  constexpr std::array<char, 2> tev_op_table{
      '+',  // TEVOP_ADD = 0,
      '-',  // TEVOP_SUB = 1,
  };

  // Regular TEV stage: (d + bias + lerp(a,b,c)) * scale
  // The GameCube/Wii GPU uses a very sophisticated algorithm for scale-lerping:
  // - c is scaled from 0..255 to 0..256, which allows dividing the result by 256 instead of 255
  // - if scale is bigger than one, it is moved inside the lerp calculation for increased accuracy
  // - a rounding bias is added before dividing by 256
  out.Write("(((tevin_d.%s %s) %s)", components, tev_bias_table[bias], tev_scale_table_left[shift]);
  out.Write(" %c ", tev_op_table[op]);
  out.Write("((((tevin_temp.%s) %s) %s) >> 8)", components, tev_scale_table_left[shift],
            (shift == 3) ? "" : tev_lerp_bias[op]);
  out.Write(") %s", tev_scale_table_right[shift]);
  out.Write(";\n");
}

static void SampleTexture(ShaderCode& out, const char* texcoords, const char* texswap, int texmap,
                          APIType ApiType)
{
  out.SetConstantsUsed(C_TEXDIMS + texmap, C_TEXDIMS + texmap);

  if (ApiType == APIType::D3D)
  {
    out.Write("iround(255.0 * Tex[%d].Sample(samp[%d], float3(%s.xy * " I_TEXDIMS
              "[%d].xy, %s))).%s;\n",
              texmap, texmap, texcoords, texmap, "0.0", texswap);
  }
  else
  {
    out.Write("iround(255.0 * texture(samp[%d], float3(%s.xy * " I_TEXDIMS "[%d].xy, %s))).%s;\n",
              texmap, texcoords, texmap, "0.0", texswap);
  }
}

constexpr std::array<const char*, 8> tev_alpha_funcs_table{
    "(false)",         // NEVER
    "(prev.a <  %s)",  // LESS
    "(prev.a == %s)",  // EQUAL
    "(prev.a <= %s)",  // LEQUAL
    "(prev.a >  %s)",  // GREATER
    "(prev.a != %s)",  // NEQUAL
    "(prev.a >= %s)",  // GEQUAL
    "(true)"           // ALWAYS
};

constexpr std::array<const char*, 4> tev_alpha_funclogic_table{
    " && ",  // and
    " || ",  // or
    " != ",  // xor
    " == "   // xnor
};

static void WriteAlphaTest(ShaderCode& out, const pixel_shader_uid_data* uid_data, APIType ApiType,
                           bool per_pixel_depth, bool use_dual_source)
{
  static constexpr std::array<const char*, 2> alpha_ref{I_ALPHA ".r", I_ALPHA ".g"};

  out.SetConstantsUsed(C_ALPHA, C_ALPHA);

  if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_NEGATED_BOOLEAN))
    out.Write("\tif(( ");
  else
    out.Write("\tif(!( ");

  // Lookup the first component from the alpha function table
  int compindex = uid_data->alpha_test_comp0;
  out.Write(tev_alpha_funcs_table[compindex], alpha_ref[0]);

  // Lookup the logic op
  out.Write("%s", tev_alpha_funclogic_table[uid_data->alpha_test_logic]);

  // Lookup the second component from the alpha function table
  compindex = uid_data->alpha_test_comp1;
  out.Write(tev_alpha_funcs_table[compindex], alpha_ref[1]);

  if (DriverDetails::HasBug(DriverDetails::BUG_BROKEN_NEGATED_BOOLEAN))
    out.Write(") == false) {\n");
  else
    out.Write(")) {\n");

  out.Write("\t\tocol0 = float4(0.0, 0.0, 0.0, 0.0);\n");
  if (use_dual_source && !(ApiType == APIType::D3D && uid_data->uint_output))
    out.Write("\t\tocol1 = float4(0.0, 0.0, 0.0, 0.0);\n");
  if (per_pixel_depth)
  {
    out.Write("\t\tdepth = %s;\n",
              !g_ActiveConfig.backend_info.bSupportsReversedDepthRange ? "0.0" : "1.0");
  }

  // ZCOMPLOC HACK:
  if (!uid_data->alpha_test_use_zcomploc_hack)
  {
    out.Write("\t\tdiscard;\n");
    if (ApiType == APIType::D3D)
      out.Write("\t\treturn;\n");
  }

  out.Write("\t}\n");
}

constexpr std::array<const char*, 8> tev_fog_funcs_table{
    "",                                                       // No Fog
    "",                                                       // ?
    "",                                                       // Linear
    "",                                                       // ?
    "\tfog = 1.0 - exp2(-8.0 * fog);\n",                      // exp
    "\tfog = 1.0 - exp2(-8.0 * fog * fog);\n",                // exp2
    "\tfog = exp2(-8.0 * (1.0 - fog));\n",                    // backward exp
    "\tfog = 1.0 - fog;\n   fog = exp2(-8.0 * fog * fog);\n"  // backward exp2
};

static void WriteFog(ShaderCode& out, const pixel_shader_uid_data* uid_data)
{
  out.SetConstantsUsed(C_FOGCOLOR, C_FOGCOLOR);
  out.SetConstantsUsed(C_FOGI, C_FOGI);
  out.SetConstantsUsed(C_FOGF, C_FOGF + 1);
  if (uid_data->fog_proj == 0)
  {
    // perspective
    // ze = A/(B - (Zs >> B_SHF)
    // TODO: Verify that we want to drop lower bits here! (currently taken over from software
    // renderer)
    //       Maybe we want to use "ze = (A << B_SHF)/((B << B_SHF) - Zs)" instead?
    //       That's equivalent, but keeps the lower bits of Zs.
    out.Write("\tfloat ze = (" I_FOGF ".x * 16777216.0) / float(" I_FOGI ".y - (zCoord >> " I_FOGI
              ".w));\n");
  }
  else
  {
    // orthographic
    // ze = a*Zs    (here, no B_SHF)
    out.Write("\tfloat ze = " I_FOGF ".x * float(zCoord) / 16777216.0;\n");
  }

  // x_adjust = sqrt((x-center)^2 + k^2)/k
  // ze *= x_adjust
  if (uid_data->fog_RangeBaseEnabled)
  {
    out.SetConstantsUsed(C_FOGF, C_FOGF);
    out.Write("\tfloat offset = (2.0 * (rawpos.x / " I_FOGF ".w)) - 1.0 - " I_FOGF ".z;\n");
    out.Write("\tfloat floatindex = clamp(9.0 - abs(offset) * 9.0, 0.0, 9.0);\n");
    out.Write("\tuint indexlower = uint(floatindex);\n");
    out.Write("\tuint indexupper = indexlower + 1u;\n");
    out.Write("\tfloat klower = " I_FOGRANGE "[indexlower >> 2u][indexlower & 3u];\n");
    out.Write("\tfloat kupper = " I_FOGRANGE "[indexupper >> 2u][indexupper & 3u];\n");
    out.Write("\tfloat k = lerp(klower, kupper, frac(floatindex));\n");
    out.Write("\tfloat x_adjust = sqrt(offset * offset + k * k) / k;\n");
    out.Write("\tze *= x_adjust;\n");
  }

  out.Write("\tfloat fog = clamp(ze - " I_FOGF ".y, 0.0, 1.0);\n");

  if (uid_data->fog_fsel > 3)
  {
    out.Write("%s", tev_fog_funcs_table[uid_data->fog_fsel]);
  }
  else
  {
    if (uid_data->fog_fsel != 2)
      WARN_LOG(VIDEO, "Unknown Fog Type! %08x", uid_data->fog_fsel);
  }

  out.Write("\tint ifog = iround(fog * 256.0);\n");
  out.Write("\tprev.rgb = (prev.rgb * (256 - ifog) + " I_FOGCOLOR ".rgb * ifog) >> 8;\n");
}

static void WriteColor(ShaderCode& out, APIType api_type, const pixel_shader_uid_data* uid_data,
                       bool use_dual_source)
{
  // D3D requires that the shader outputs be uint when writing to a uint render target for logic op.
  if (api_type == APIType::D3D && uid_data->uint_output)
  {
    if (uid_data->rgba6_format)
      out.Write("\tocol0 = uint4(prev & 0xFC);\n");
    else
      out.Write("\tocol0 = uint4(prev);\n");
    return;
  }

  // Use dual-source color blending to perform dst alpha in a single pass
  if (use_dual_source)
    out.Write("\tocol1 = float4(0.0, 0.0, 0.0, float(prev.a) / 255.0);\n");

  // Colors will be blended against the 8-bit alpha from ocol1 and
  // the 6-bit alpha from ocol0 will be written to the framebuffer
  if (uid_data->useDstAlpha)
  {
    out.SetConstantsUsed(C_ALPHA, C_ALPHA);
    out.Write("\tprev.a = " I_ALPHA ".a;\n");
  }

  if (uid_data->rgba6_format)
    out.Write("\tocol0 = float4(prev >> 2) / 63.0;\n");
  else
    out.Write("\tocol0 = float4(prev) / 255.0;\n");
}

static void WriteLogicOp(ShaderCode& out, const pixel_shader_uid_data* uid_data)
{
  constexpr std::array<const char*, 16> logicOps{{
      "\tnew_color = int3(0, 0, 0);\n",             // CLEAR
      "\tnew_color = new_color & old_color;\n",     // AND
      "\tnew_color = new_color & (~old_color);\n",  // AND_REVERSE
      "\n",                                         // COPY
      "\tnew_color = (~new_color) & old_color;\n",  // AND_INVERTED
      "\tnew_color = old_color;\n",                 // NOOP
      "\tnew_color = new_color ^ old_color;\n",     // XOR
      "\tnew_color = new_color | old_color;\n",     // OR
      "\tnew_color = ~(new_color | old_color);\n",  // NOR
      "\tnew_color = ~(new_color ^ old_color);\n",  // EQUIV
      "\tnew_color = ~old_color;\n",                // INVERT
      "\tnew_color = new_color | (~old_color);\n",  // OR_REVERSE
      "\tnew_color = ~new_color;\n",                // COPY_INVERTED
      "\tnew_color = (~new_color) | old_color;\n",  // OR_INVERTED
      "\tnew_color = ~(new_color & old_color);\n",  // NAND
      "\tnew_color = int3(255, 255, 255);\n",       // SET
  }};
  out.Write("\tint3 old_color = int3(initial_ocol0.rgb * 255.0);\n");
  out.Write("\tint3 new_color = int3(ocol0.rgb * 255.0);\n");
  out.Write("%s", logicOps[uid_data->logic_mode]);
  out.Write("\tocol0.rgb = float3(new_color & 255) / 255.0;\n");

  //
  out.Write("\treal_ocol0 = ocol0;\n");
}

static void WriteZCoord(ShaderCode& out, APIType api_type, const ShaderHostConfig& host_config,
                        const pixel_shader_uid_data* uid_data)
{
  if (uid_data->zfreeze)
  {
    out.SetConstantsUsed(C_ZSLOPE, C_ZSLOPE);
    out.SetConstantsUsed(C_EFBSCALE, C_EFBSCALE);

    out.Write("\tfloat2 screenpos = rawpos.xy * " I_EFBSCALE ".xy;\n");

    // Opengl has reversed vertical screenspace coordinates
    if (api_type == APIType::OpenGL)
      out.Write("\tscreenpos.y = %i.0 - screenpos.y;\n", EFB_HEIGHT);

    out.Write("\tint zCoord = int(" I_ZSLOPE ".z + " I_ZSLOPE ".x * screenpos.x + " I_ZSLOPE
              ".y * screenpos.y);\n");
  }
  else if (!host_config.fast_depth_calc)
  {
    // FastDepth means to trust the depth generated in perspective division.
    // It should be correct, but it seems not to be as accurate as required. TODO: Find out why!
    // For disabled FastDepth we just calculate the depth value again.
    // The performance impact of this additional calculation doesn't matter, but it prevents
    // the host GPU driver from performing any early depth test optimizations.
    out.SetConstantsUsed(C_ZBIAS + 1, C_ZBIAS + 1);
    // the screen space depth value = far z + (clip z / clip w) * z range
    out.Write("\tint zCoord = " I_ZBIAS "[1].x + int((clipPos.z / clipPos.w) * float(" I_ZBIAS
              "[1].y));\n");
  }
  else
  {
    if (!host_config.backend_reversed_depth_range)
      out.Write("\tint zCoord = int((1.0 - rawpos.z) * 16777216.0);\n");
    else
      out.Write("\tint zCoord = int(rawpos.z * 16777216.0);\n");
  }
  out.Write("\tzCoord = clamp(zCoord, 0, 0xFFFFFF);\n");
}
