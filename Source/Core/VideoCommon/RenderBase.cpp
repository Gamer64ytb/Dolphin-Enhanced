// Copyright 2010 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

// ---------------------------------------------------------------------------------------------
// GC graphics pipeline
// ---------------------------------------------------------------------------------------------
// 3d commands are issued through the fifo. The GPU draws to the 2MB EFB.
// The efb can be copied back into ram in two forms: as textures or as XFB.
// The XFB is the region in RAM that the VI chip scans out to the television.
// So, after all rendering to EFB is done, the image is copied into one of two XFBs in RAM.
// Next frame, that one is scanned out and the other one gets the copy. = double buffering.
// ---------------------------------------------------------------------------------------------

#include "VideoCommon/RenderBase.h"

#include <algorithm>
#include <cinttypes>
#include <cmath>
#include <memory>
#include <mutex>
#include <string>
#include <tuple>

#include "Common/Assert.h"
#include "Common/CommonTypes.h"
#include "Common/Config/Config.h"
#include "Common/Event.h"
#include "Common/FileUtil.h"
#include "Common/Flag.h"
#include "Common/Logging/Log.h"
#include "Common/MsgHandler.h"
#include "Common/Profiler.h"
#include "Common/StringUtil.h"
#include "Common/Thread.h"
#include "Common/Timer.h"

#include "Core/Config/SYSCONFSettings.h"
#include "Core/ConfigManager.h"
#include "Core/Core.h"
#include "Core/FifoPlayer/FifoRecorder.h"
#include "Core/HW/VideoInterface.h"
#include "Core/Host.h"
#include "Core/Movie.h"

#include "VideoCommon/AbstractFramebuffer.h"
#include "VideoCommon/AbstractStagingTexture.h"
#include "VideoCommon/AbstractTexture.h"
#include "VideoCommon/BPFunctions.h"
#include "VideoCommon/BPMemory.h"
#include "VideoCommon/CPMemory.h"
#include "VideoCommon/CommandProcessor.h"
#include "VideoCommon/FrameDump.h"
#include "VideoCommon/FramebufferManager.h"
#include "VideoCommon/ImageWrite.h"
#include "VideoCommon/OnScreenDisplay.h"
#include "VideoCommon/PixelEngine.h"
#include "VideoCommon/PixelShaderManager.h"
#include "VideoCommon/PostProcessing.h"
#include "VideoCommon/RasterFont.h"
#include "VideoCommon/ShaderCache.h"
#include "VideoCommon/ShaderGenCommon.h"
#include "VideoCommon/Statistics.h"
#include "VideoCommon/TextureCacheBase.h"
#include "VideoCommon/TextureDecoder.h"
#include "VideoCommon/VertexLoaderManager.h"
#include "VideoCommon/VertexManagerBase.h"
#include "VideoCommon/VertexShaderManager.h"
#include "VideoCommon/VideoBackendBase.h"
#include "VideoCommon/VideoCommon.h"
#include "VideoCommon/VideoConfig.h"
#include "VideoCommon/XFMemory.h"

std::unique_ptr<Renderer> g_renderer;

static float AspectToWidescreen(float aspect)
{
  return aspect * ((16.0f / 9.0f) / (4.0f / 3.0f));
}

Renderer::Renderer(int backbuffer_width, int backbuffer_height, float backbuffer_scale,
                   AbstractTextureFormat backbuffer_format)
    : m_backbuffer_width(backbuffer_width), m_backbuffer_height(backbuffer_height),
      m_backbuffer_scale(backbuffer_scale),
      m_backbuffer_format(backbuffer_format), m_last_xfb_width{MAX_XFB_WIDTH}, m_last_xfb_height{
                                                                                   MAX_XFB_HEIGHT}
{
  m_aspect_wide = SConfig::GetInstance().bWii && Config::Get(Config::SYSCONF_WIDESCREEN);
  UpdateActiveConfig();
}

Renderer::~Renderer() = default;

bool Renderer::Initialize()
{
  UpdateDrawRectangle();
  CalculateTargetSize();

  m_raster_font = std::make_unique<VideoCommon::RasterFont>();
  if (!m_raster_font->Initialize(m_backbuffer_format))
    return false;

  m_post_processor = std::make_unique<VideoCommon::PostProcessing>();
  return m_post_processor->Initialize(m_backbuffer_format);
}

void Renderer::Shutdown()
{
  // First stop any framedumping, which might need to dump the last xfb frame. This process
  // can require additional graphics sub-systems so it needs to be done first
  ShutdownFrameDumping();
  m_post_processor.reset();
  m_raster_font.reset();
}

void Renderer::BeginUtilityDrawing()
{
  g_vertex_manager->Flush();
}

void Renderer::EndUtilityDrawing()
{
  // Reset framebuffer/scissor/viewport. Pipeline will be reset at next draw.
  g_framebuffer_manager->BindEFBFramebuffer();
  BPFunctions::SetScissor();
  BPFunctions::SetViewport();
}

void Renderer::SetFramebuffer(AbstractFramebuffer* framebuffer)
{
  m_current_framebuffer = framebuffer;
}

void Renderer::SetAndDiscardFramebuffer(AbstractFramebuffer* framebuffer)
{
  m_current_framebuffer = framebuffer;
}

void Renderer::SetAndClearFramebuffer(AbstractFramebuffer* framebuffer,
                                      const ClearColor& color_value, float depth_value)
{
  m_current_framebuffer = framebuffer;
}

bool Renderer::EFBHasAlphaChannel() const
{
  return m_prev_efb_format == PEControl::RGBA6_Z24;
}

void Renderer::ClearScreen(const MathUtil::Rectangle<int>& rc, bool colorEnable, bool alphaEnable,
                           bool zEnable, u32 color, u32 z)
{
  g_framebuffer_manager->ClearEFB(rc, colorEnable, alphaEnable, zEnable, color, z);
}

void Renderer::ReinterpretPixelData(EFBReinterpretType convtype)
{
  g_framebuffer_manager->ReinterpretPixelData(convtype);
}

u32 Renderer::AccessEFB(EFBAccessType type, u32 x, u32 y, u32 poke_data)
{
  if (type == EFBAccessType::PeekColor)
  {
    u32 color = g_framebuffer_manager->PeekEFBColor(x, y);

    // a little-endian value is expected to be returned
    color = ((color & 0xFF00FF00) | ((color >> 16) & 0xFF) | ((color << 16) & 0xFF0000));

    // check what to do with the alpha channel (GX_PokeAlphaRead)
    PixelEngine::UPEAlphaReadReg alpha_read_mode = PixelEngine::GetAlphaReadMode();

    if (bpmem.zcontrol.pixel_format == PEControl::RGBA6_Z24)
    {
      color = RGBA8ToRGBA6ToRGBA8(color);
    }
    else if (bpmem.zcontrol.pixel_format == PEControl::RGB565_Z16)
    {
      color = RGBA8ToRGB565ToRGBA8(color);
    }
    if (bpmem.zcontrol.pixel_format != PEControl::RGBA6_Z24)
    {
      color |= 0xFF000000;
    }

    if (alpha_read_mode.ReadMode == 2)
    {
      return color;  // GX_READ_NONE
    }
    else if (alpha_read_mode.ReadMode == 1)
    {
      return color | 0xFF000000;  // GX_READ_FF
    }
    else /*if(alpha_read_mode.ReadMode == 0)*/
    {
      return color & 0x00FFFFFF;  // GX_READ_00
    }
  }
  else  // if (type == EFBAccessType::PeekZ)
  {
    // Depth buffer is inverted for improved precision near far plane
    float depth = g_framebuffer_manager->PeekEFBDepth(x, y);
    if (!g_ActiveConfig.backend_info.bSupportsReversedDepthRange)
      depth = 1.0f - depth;

    u32 ret = 0;
    if (bpmem.zcontrol.pixel_format == PEControl::RGB565_Z16)
    {
      // if Z is in 16 bit format you must return a 16 bit integer
      ret = std::clamp<u32>(static_cast<u32>(depth * 65536.0f), 0, 0xFFFF);
    }
    else
    {
      ret = std::clamp<u32>(static_cast<u32>(depth * 16777216.0f), 0, 0xFFFFFF);
    }

    return ret;
  }
}

void Renderer::PokeEFB(EFBAccessType type, const EfbPokeData* points, size_t num_points)
{
  if (type == EFBAccessType::PokeColor)
  {
    for (size_t i = 0; i < num_points; i++)
    {
      // Convert to expected format (BGRA->RGBA)
      // TODO: Check alpha, depending on mode?
      const EfbPokeData& point = points[i];
      u32 color = ((point.data & 0xFF00FF00) | ((point.data >> 16) & 0xFF) |
                   ((point.data << 16) & 0xFF0000));
      g_framebuffer_manager->PokeEFBColor(point.x, point.y, color);
    }
  }
  else  // if (type == EFBAccessType::PokeZ)
  {
    for (size_t i = 0; i < num_points; i++)
    {
      // Convert to floating-point depth.
      const EfbPokeData& point = points[i];
      float depth = float(point.data & 0xFFFFFF) / 16777216.0f;
      if (!g_ActiveConfig.backend_info.bSupportsReversedDepthRange)
        depth = 1.0f - depth;

      g_framebuffer_manager->PokeEFBDepth(point.x, point.y, depth);
    }
  }
}

void Renderer::RenderToXFB(u32 xfbAddr, const MathUtil::Rectangle<int>& sourceRc, u32 fbStride,
                           u32 fbHeight, float Gamma)
{
  CheckFifoRecording();

  if (!fbStride || !fbHeight)
    return;
}

float Renderer::GetEFBScale() const
{
  return m_efb_scale / 100.0f;
}

bool Renderer::IsScaledEFB() const
{
  return m_efb_scale != 100;
}

int Renderer::EFBToScaledX(int x) const
{
  return x * m_efb_scale / 100.0f;
}

int Renderer::EFBToScaledY(int y) const
{
  return y * m_efb_scale / 100.0f;
}

float Renderer::EFBToScaledXf(float x) const
{
  return x * ((float)GetTargetWidth() / (float)EFB_WIDTH);
}

float Renderer::EFBToScaledYf(float y) const
{
  return y * ((float)GetTargetHeight() / (float)EFB_HEIGHT);
}

std::tuple<int, int> Renderer::CalculateTargetScale(int x, int y) const
{
  return std::make_tuple(x * m_efb_scale / 100.0f, y * m_efb_scale / 100.0f);
}

// return true if target size changed
bool Renderer::CalculateTargetSize()
{
  if (g_ActiveConfig.iEFBScale == EFB_SCALE_AUTO_INTEGRAL)
  {
    // Set a scale based on the window size
    int width = EFB_WIDTH * m_target_rectangle.GetWidth() * 100 / m_last_xfb_width;
    int height = EFB_HEIGHT * m_target_rectangle.GetHeight() * 100 / m_last_xfb_height;
    m_efb_scale = std::max((width - 1) / EFB_WIDTH + 1, (height - 1) / EFB_HEIGHT + 1);
  }
  else
  {
    m_efb_scale = g_ActiveConfig.iEFBScale;
    if (m_efb_scale < 10)
      m_efb_scale *= 100;
  }

  const u32 max_size = g_ActiveConfig.backend_info.MaxTextureSize;
  if (max_size < EFB_WIDTH * m_efb_scale / 100)
    m_efb_scale = max_size * 100 / EFB_WIDTH;

  int new_efb_width = 0;
  int new_efb_height = 0;
  std::tie(new_efb_width, new_efb_height) = CalculateTargetScale(EFB_WIDTH, EFB_HEIGHT);
  new_efb_width = std::max(new_efb_width, 1);
  new_efb_height = std::max(new_efb_height, 1);

  if (new_efb_width != m_target_width || new_efb_height != m_target_height)
  {
    std::string msg;
    const char* backend = SConfig::GetInstance().m_strVideoBackend.c_str();
    if (SConfig::GetInstance().bMMU)
    {
      msg = StringFromFormat("Backend: %s - MMU: On - Scale: %.02f", backend, m_efb_scale / 100.0f);
    }
    else
    {
      msg = StringFromFormat("Backend: %s - Scale: %.02f", backend, m_efb_scale / 100.0f);
    }
    OSD::AddTypedMessage(OSD::MessageType::EFBScale, msg, 4000);
    m_target_width = new_efb_width;
    m_target_height = new_efb_height;
    PixelShaderManager::SetEfbScaleChanged(EFBToScaledXf(1), EFBToScaledYf(1));
    return true;
  }
  return false;
}

void Renderer::SaveScreenshot(const std::string& filename, bool wait_for_completion)
{
  // We must not hold the lock while waiting for the screenshot to complete.
  {
    std::lock_guard<std::mutex> lk(m_screenshot_lock);
    m_screenshot_name = filename;
    m_screenshot_request.Set();
  }

  if (wait_for_completion)
  {
    // This is currently only used by Android, and it was using a wait time of 2 seconds.
    m_screenshot_completed.WaitFor(std::chrono::seconds(2));
  }
}

void Renderer::CheckForConfigChanges()
{
  const ShaderHostConfig old_shader_host_config = ShaderHostConfig::GetCurrent();
  const u32 old_multisamples = g_ActiveConfig.iMultisamples;
  const int old_anisotropy = g_ActiveConfig.iMaxAnisotropy;
  const int old_efb_access_tile_size = g_ActiveConfig.iEFBAccessTileSize;
  const bool old_force_filtering = g_ActiveConfig.bForceFiltering;
  const bool old_vsync = g_ActiveConfig.bVSyncActive;
  const bool old_bbox = g_ActiveConfig.bBBoxEnable;

  UpdateActiveConfig();

  // Update texture cache settings with any changed options.
  g_texture_cache->OnConfigChanged(g_ActiveConfig);

  // EFB tile cache doesn't need to notify the backend.
  if (old_efb_access_tile_size != g_ActiveConfig.iEFBAccessTileSize)
    g_framebuffer_manager->SetEFBCacheTileSize(std::max(g_ActiveConfig.iEFBAccessTileSize, 0));

  // Check for post-processing shader changes. Done up here as it doesn't affect anything outside
  // the post-processor. Note that options are applied every frame, so no need to check those.
  if (m_post_processor->GetConfig()->GetShader() != g_ActiveConfig.sPostProcessingShader)
  {
    OSD::AddMessage(StringFromFormat("Reload post processing shader: %s",
                                     g_ActiveConfig.sPostProcessingShader.c_str()));

    // The existing shader must not be in use when it's destroyed
    WaitForGPUIdle();

    m_post_processor->RecompileShader();
  }

  // Determine which (if any) settings have changed.
  ShaderHostConfig new_host_config = ShaderHostConfig::GetCurrent();
  u32 changed_bits = 0;
  if (old_shader_host_config.bits != new_host_config.bits)
    changed_bits |= CONFIG_CHANGE_BIT_HOST_CONFIG;
  if (old_multisamples != g_ActiveConfig.iMultisamples)
    changed_bits |= CONFIG_CHANGE_BIT_MULTISAMPLES;
  if (old_anisotropy != g_ActiveConfig.iMaxAnisotropy)
    changed_bits |= CONFIG_CHANGE_BIT_ANISOTROPY;
  if (old_force_filtering != g_ActiveConfig.bForceFiltering)
    changed_bits |= CONFIG_CHANGE_BIT_FORCE_TEXTURE_FILTERING;
  if (old_vsync != g_ActiveConfig.bVSyncActive)
    changed_bits |= CONFIG_CHANGE_BIT_VSYNC;
  if (old_bbox != g_ActiveConfig.bBBoxEnable)
    changed_bits |= CONFIG_CHANGE_BIT_BBOX;
  if (CalculateTargetSize())
    changed_bits |= CONFIG_CHANGE_BIT_TARGET_SIZE;

  // No changes?
  if (changed_bits == 0)
    return;

  // Notify the backend of the changes, if any.
  OnConfigChanged(changed_bits);

  // Framebuffer changed?
  if (changed_bits & (CONFIG_CHANGE_BIT_MULTISAMPLES | CONFIG_CHANGE_BIT_TARGET_SIZE))
  {
    g_framebuffer_manager->RecreateEFBFramebuffer();
  }

  // Reload shaders if host config has changed.
  if (changed_bits & (CONFIG_CHANGE_BIT_HOST_CONFIG | CONFIG_CHANGE_BIT_MULTISAMPLES))
  {
    OSD::AddMessage("Video config changed, reloading shaders.", OSD::Duration::NORMAL);
    WaitForGPUIdle();
    SetPipeline(nullptr);
    g_vertex_manager->InvalidatePipelineObject();
    g_shader_cache->SetHostConfig(new_host_config);
    g_shader_cache->Reload();
    g_framebuffer_manager->RecompileShaders();
  }

  // Viewport and scissor rect have to be reset since they will be scaled differently.
  if (changed_bits & CONFIG_CHANGE_BIT_TARGET_SIZE)
  {
    BPFunctions::SetViewport();
    BPFunctions::SetScissor();
  }
}

// Create On-Screen-Messages
void Renderer::DrawDebugText()
{
  const Core::PerformanceStatistics& p_stats = Core::GetPerformanceStatistics();

  u32 color;
  if (p_stats.Speed > 90)
    color = 0xFF00FFFF; // Blue
  else
    color = 0xFFFF0000; // Red

  RenderText(m_debug_title_text, 10, 18, color);
}

void Renderer::RenderText(const std::string& text, int left, int top, u32 color)
{
  m_raster_font->Draw(text, left, top, color);
}

float Renderer::CalculateDrawAspectRatio() const
{
  if (g_ActiveConfig.aspect_mode == AspectMode::Stretch)
  {
    // If stretch is enabled, we prefer the aspect ratio of the window.
    return (static_cast<float>(m_backbuffer_width) / static_cast<float>(m_backbuffer_height));
  }

  // The rendering window aspect ratio as a proportion of the 4:3 or 16:9 ratio
  if (g_ActiveConfig.aspect_mode == AspectMode::AnalogWide ||
      (g_ActiveConfig.aspect_mode != AspectMode::Analog && m_aspect_wide))
  {
    return AspectToWidescreen(VideoInterface::GetAspectRatio());
  }
  else
  {
    return VideoInterface::GetAspectRatio();
  }
}

void Renderer::AdjustRectanglesToFitBounds(MathUtil::Rectangle<int>& dst,
                                           MathUtil::Rectangle<int>& src)
{
  float scale = g_ActiveConfig.fDisplayScale;

  int delta_x = std::lround(dst.GetWidth() * (scale - 1.0f) / 4.0f);
  int delta_y = std::lround(dst.GetHeight() * (scale - 1.0f) / 4.0f);

  dst.left = dst.left - delta_x;
  dst.top = dst.top - delta_y;
  dst.right = dst.right + delta_x;
  dst.bottom = dst.bottom + delta_y;

  if (dst.GetWidth() > m_backbuffer_width)
  {
    delta_x =
        std::lround(src.GetWidth() * (1.0f - m_backbuffer_width / (float)dst.GetWidth()) / 2.0f);
    src.left += delta_x;
    src.right -= delta_x;

    dst.left = 0;
    dst.right = m_backbuffer_width;
  }

  if (dst.GetHeight() > m_backbuffer_height)
  {
    delta_y =
        std::lround(src.GetHeight() * (1.0f - m_backbuffer_height / (float)dst.GetHeight()) / 2.0f);
    src.top += delta_y;
    src.bottom -= delta_y;

    dst.top = 0;
    dst.bottom = m_backbuffer_height;
  }
}

bool Renderer::IsHeadless() const
{
  return true;
}

void Renderer::ChangeSurface(void* new_surface_handle)
{
  std::lock_guard<std::mutex> lock(m_swap_mutex);
  m_new_surface_handle = new_surface_handle;
  m_surface_changed.Set();
}

void Renderer::ResizeSurface()
{
  std::lock_guard<std::mutex> lock(m_swap_mutex);
  m_surface_resized.Set();
}

void Renderer::SetViewportAndScissor(const MathUtil::Rectangle<int>& rect, float min_depth,
                                     float max_depth)
{
  SetViewport(static_cast<float>(rect.left), static_cast<float>(rect.top),
              static_cast<float>(rect.GetWidth()), static_cast<float>(rect.GetHeight()), min_depth,
              max_depth);
  SetScissorRect(rect);
}

void Renderer::ScaleTexture(AbstractFramebuffer* dst_framebuffer,
                            const MathUtil::Rectangle<int>& dst_rect,
                            const AbstractTexture* src_texture,
                            const MathUtil::Rectangle<int>& src_rect)
{
  ASSERT(dst_framebuffer->GetColorFormat() == AbstractTextureFormat::RGBA8);

  BeginUtilityDrawing();

  // The shader needs to know the source rectangle.
  const auto converted_src_rect = g_renderer->ConvertFramebufferRectangle(
      src_rect, src_texture->GetWidth(), src_texture->GetHeight());
  const float rcp_src_width = 1.0f / src_texture->GetWidth();
  const float rcp_src_height = 1.0f / src_texture->GetHeight();
  const std::array<float, 4> uniforms = {{converted_src_rect.left * rcp_src_width,
                                          converted_src_rect.top * rcp_src_height,
                                          converted_src_rect.GetWidth() * rcp_src_width,
                                          converted_src_rect.GetHeight() * rcp_src_height}};
  g_vertex_manager->UploadUtilityUniforms(&uniforms, sizeof(uniforms));

  // Discard if we're overwriting the whole thing.
  if (static_cast<u32>(dst_rect.GetWidth()) == dst_framebuffer->GetWidth() &&
      static_cast<u32>(dst_rect.GetHeight()) == dst_framebuffer->GetHeight())
  {
    SetAndDiscardFramebuffer(dst_framebuffer);
  }
  else
  {
    SetFramebuffer(dst_framebuffer);
  }

  SetViewportAndScissor(ConvertFramebufferRectangle(dst_rect, dst_framebuffer));
  SetPipeline(g_shader_cache->GetRGBA8CopyPipeline());
  SetTexture(0, src_texture);
  SetSamplerState(0, RenderState::GetLinearSamplerState());
  Draw(0, 3);
  EndUtilityDrawing();
  if (dst_framebuffer->GetColorAttachment())
    dst_framebuffer->GetColorAttachment()->FinishedRendering();
}

MathUtil::Rectangle<int>
Renderer::ConvertFramebufferRectangle(const MathUtil::Rectangle<int>& rect,
                                      const AbstractFramebuffer* framebuffer)
{
  return ConvertFramebufferRectangle(rect, framebuffer->GetWidth(), framebuffer->GetHeight());
}

MathUtil::Rectangle<int> Renderer::ConvertFramebufferRectangle(const MathUtil::Rectangle<int>& rect,
                                                               u32 fb_width, u32 fb_height)
{
  MathUtil::Rectangle<int> ret = rect;
  // In OpenGL we have a left-hand NDC(Normalized Device Coordinates) space, y is upward
  // in Vulkan we have a right-hand NDC(Normalized Device Coordinates) space, y is downward
  if (g_ActiveConfig.backend_info.bUsesLowerLeftOrigin)
  {
    ret.top = fb_height - rect.bottom;
    ret.bottom = fb_height - rect.top;
  }
  return ret;
}

MathUtil::Rectangle<int> Renderer::ConvertEFBRectangle(const MathUtil::Rectangle<int>& rc)
{
  MathUtil::Rectangle<int> result;
  result.left = EFBToScaledX(rc.left);
  result.top = EFBToScaledY(rc.top);
  result.right = EFBToScaledX(rc.right);
  result.bottom = EFBToScaledY(rc.bottom);
  return result;
}

std::tuple<float, float> Renderer::ScaleToDisplayAspectRatio(const int width,
                                                             const int height) const
{
  // Scale either the width or height depending the content aspect ratio.
  // This way we preserve as much resolution as possible when scaling.
  float scaled_width = static_cast<float>(width);
  float scaled_height = static_cast<float>(height);
  const float draw_aspect = CalculateDrawAspectRatio();
  if (scaled_width / scaled_height >= draw_aspect)
    scaled_height = scaled_width / draw_aspect;
  else
    scaled_width = scaled_height * draw_aspect;
  return std::make_tuple(scaled_width, scaled_height);
}

void Renderer::UpdateDrawRectangle()
{
  // The rendering window size
  const float win_width = static_cast<float>(m_backbuffer_width);
  const float win_height = static_cast<float>(m_backbuffer_height);

  // Update aspect ratio hack values
  // Won't take effect until next frame
  // Don't know if there is a better place for this code so there isn't a 1 frame delay
  if (g_ActiveConfig.bWidescreenHack)
  {
    float source_aspect = VideoInterface::GetAspectRatio();
    if (m_aspect_wide)
      source_aspect = AspectToWidescreen(source_aspect);
    float target_aspect = 0.0f;

    switch (g_ActiveConfig.aspect_mode)
    {
    case AspectMode::Stretch:
      target_aspect = win_width / win_height;
      break;
    case AspectMode::Analog:
      target_aspect = VideoInterface::GetAspectRatio();
      break;
    case AspectMode::AnalogWide:
      target_aspect = AspectToWidescreen(VideoInterface::GetAspectRatio());
      break;
    case AspectMode::Auto:
    default:
      target_aspect = source_aspect;
      break;
    }

    float adjust = source_aspect / target_aspect;
    if (adjust > 1)
    {
      // Vert+
      g_Config.fAspectRatioHackW = 1;
      g_Config.fAspectRatioHackH = 1 / adjust;
    }
    else
    {
      // Hor+
      g_Config.fAspectRatioHackW = adjust;
      g_Config.fAspectRatioHackH = 1;
    }
  }
  else
  {
    // Hack is disabled
    g_Config.fAspectRatioHackW = 1;
    g_Config.fAspectRatioHackH = 1;
  }

  float draw_width, draw_height, crop_width, crop_height;

  // get the picture aspect ratio
  draw_width = crop_width = CalculateDrawAspectRatio();
  draw_height = crop_height = 1;

  // crop the picture to a standard aspect ratio
  if (g_ActiveConfig.bCrop && g_ActiveConfig.aspect_mode != AspectMode::Stretch)
  {
    float expected_aspect = (g_ActiveConfig.aspect_mode == AspectMode::AnalogWide ||
                             (g_ActiveConfig.aspect_mode != AspectMode::Analog && m_aspect_wide)) ?
                                (16.0f / 9.0f) :
                                (4.0f / 3.0f);
    if (crop_width / crop_height >= expected_aspect)
    {
      // the picture is flatter than it should be
      crop_width = crop_height * expected_aspect;
    }
    else
    {
      // the picture is skinnier than it should be
      crop_height = crop_width / expected_aspect;
    }
  }

  // scale the picture to fit the rendering window
  if (win_width / win_height >= crop_width / crop_height)
  {
    // the window is flatter than the picture
    draw_width *= win_height / crop_height;
    crop_width *= win_height / crop_height;
    draw_height *= win_height / crop_height;
    crop_height = win_height;
  }
  else
  {
    // the window is skinnier than the picture
    draw_width *= win_width / crop_width;
    draw_height *= win_width / crop_width;
    crop_height *= win_width / crop_width;
    crop_width = win_width;
  }

  // ensure divisibility by 4 to make it compatible with all the video encoders
  draw_width = std::ceil(draw_width) - static_cast<int>(std::ceil(draw_width)) % 4;
  draw_height = std::ceil(draw_height) - static_cast<int>(std::ceil(draw_height)) % 4;

  m_target_rectangle.left = static_cast<int>(win_width - draw_width) / 2;
  m_target_rectangle.top = static_cast<int>(win_height - draw_height) / 2;
  m_target_rectangle.right = m_target_rectangle.left + static_cast<int>(draw_width);
  m_target_rectangle.bottom = m_target_rectangle.top + static_cast<int>(draw_height);
}

void Renderer::SetWindowSize(int width, int height)
{
  std::tie(width, height) = CalculateOutputDimensions(width, height);

  // Track the last values of width/height to avoid sending a window resize event every frame.
  if (width != m_last_window_request_width || height != m_last_window_request_height)
  {
    m_last_window_request_width = width;
    m_last_window_request_height = height;
    Host_RequestRenderWindowSize(width, height);
  }
}

std::tuple<int, int> Renderer::CalculateOutputDimensions(int width, int height)
{
  width = std::max(width, 1);
  height = std::max(height, 1);

  float scaled_width, scaled_height;
  std::tie(scaled_width, scaled_height) = ScaleToDisplayAspectRatio(width, height);

  if (g_ActiveConfig.bCrop)
  {
    // Force 4:3 or 16:9 by cropping the image.
    float current_aspect = scaled_width / scaled_height;
    float expected_aspect = (g_ActiveConfig.aspect_mode == AspectMode::AnalogWide ||
                             (g_ActiveConfig.aspect_mode != AspectMode::Analog && m_aspect_wide)) ?
                                (16.0f / 9.0f) :
                                (4.0f / 3.0f);
    if (current_aspect > expected_aspect)
    {
      // keep height, crop width
      scaled_width = scaled_height * expected_aspect;
    }
    else
    {
      // keep width, crop height
      scaled_height = scaled_width / expected_aspect;
    }
  }

  width = static_cast<int>(std::ceil(scaled_width));
  height = static_cast<int>(std::ceil(scaled_height));

  // UpdateDrawRectangle() makes sure that the rendered image is divisible by four for video
  // encoders, so do that here too to match it
  width -= width % 4;
  height -= height % 4;

  return std::make_tuple(width, height);
}

void Renderer::CheckFifoRecording()
{
  bool wasRecording = g_bRecordFifoData;
  g_bRecordFifoData = FifoRecorder::GetInstance().IsRecording();

  if (g_bRecordFifoData)
  {
    if (!wasRecording)
    {
      RecordVideoMemory();
    }

    FifoRecorder::GetInstance().EndFrame(CommandProcessor::fifo.CPBase,
                                         CommandProcessor::fifo.CPEnd);
  }
}

void Renderer::RecordVideoMemory()
{
  const u32* bpmem_ptr = reinterpret_cast<const u32*>(&bpmem);
  u32 cpmem[256] = {};
  // The FIFO recording format splits XF memory into xfmem and xfregs; follow
  // that split here.
  const u32* xfmem_ptr = reinterpret_cast<const u32*>(&xfmem);
  const u32* xfregs_ptr = reinterpret_cast<const u32*>(&xfmem) + FifoDataFile::XF_MEM_SIZE;
  u32 xfregs_size = sizeof(XFMemory) / 4 - FifoDataFile::XF_MEM_SIZE;

  FillCPMemoryArray(cpmem);

  FifoRecorder::GetInstance().SetVideoMemory(bpmem_ptr, cpmem, xfmem_ptr, xfregs_ptr, xfregs_size,
                                             texMem);
}

void Renderer::Swap(u32 xfb_addr, u32 fb_width, u32 fb_stride, u32 fb_height, u64 ticks)
{
  const AspectMode suggested = g_ActiveConfig.suggested_aspect_mode;
  if (suggested == AspectMode::Analog || suggested == AspectMode::AnalogWide)
  {
    m_aspect_wide = suggested == AspectMode::AnalogWide;
  }
  else if (SConfig::GetInstance().bWii)
  {
    m_aspect_wide = Config::Get(Config::SYSCONF_WIDESCREEN);
  }
  else
  {
    // Heuristic to detect if a GameCube game is in 16:9 anamorphic widescreen mode.

    size_t flush_count_4_3, flush_count_anamorphic;
    std::tie(flush_count_4_3, flush_count_anamorphic) =
        g_vertex_manager->ResetFlushAspectRatioCount();
    size_t flush_total = flush_count_4_3 + flush_count_anamorphic;

    // Modify the threshold based on which aspect ratio we're already using: if
    // the game's in 4:3, it probably won't switch to anamorphic, and vice-versa.
    if (m_aspect_wide)
      m_aspect_wide = !(flush_count_4_3 > 0.75 * flush_total);
    else
      m_aspect_wide = flush_count_anamorphic > 0.75 * flush_total;
  }

  // Ensure the last frame was written to the dump.
  // This is required even if frame dumping has stopped, since the frame dump is one frame
  // behind the renderer.
  FlushFrameDump();

  g_framebuffer_manager->EndOfFrame();

  if (xfb_addr && fb_width && fb_stride && fb_height)
  {
    // Get the current XFB from texture cache
    MathUtil::Rectangle<int> xfb_rect;
    const auto* xfb_entry =
        g_texture_cache->GetXFBTexture(xfb_addr, fb_width, fb_height, fb_stride, &xfb_rect);
    if (xfb_entry &&
        (!g_ActiveConfig.bSkipPresentingDuplicateXFBs || xfb_entry->id != m_last_xfb_id))
    {
      const bool is_duplicate_frame = xfb_entry->id == m_last_xfb_id;
      m_last_xfb_id = xfb_entry->id;

      // Since we use the common pipelines here and draw vertices if a batch is currently being
      // built by the vertex loader, we end up trampling over its pointer, as we share the buffer
      // with the loader, and it has not been unmapped yet. Force a pipeline flush to avoid this.
      // Render the XFB to the screen.
      BeginUtilityDrawing();
      if (!IsHeadless())
      {
        BindBackbuffer({0.0f, 0.0f, 0.0f, 1.0f});
        UpdateDrawRectangle();

        // Adjust the source rectangle instead of using an oversized viewport to render the XFB.
        auto render_target_rc = m_target_rectangle;
        auto render_source_rc = xfb_rect;
        AdjustRectanglesToFitBounds(render_target_rc, render_source_rc);
        RenderXFBToScreen(render_target_rc, xfb_entry->texture.get(), render_source_rc);

        // HUD
        m_raster_font->Prepare();
        DrawDebugText();
        OSD::DrawMessages();

        // Present to the window system.
        {
          std::lock_guard<std::mutex> guard(m_swap_mutex);
          PresentBackbuffer();
        }

        // Update the window size based on the frame that was just rendered.
        // Due to depending on guest state, we need to call this every frame.
        SetWindowSize(xfb_rect.GetWidth(), xfb_rect.GetHeight());
      }

      if (!is_duplicate_frame)
      {
        if (IsFrameDumping())
          DumpCurrentFrame(xfb_entry->texture.get(), xfb_rect, ticks);

        // Begin new frame
        m_frame_count++;
        g_stats.ResetFrame();
      }

      g_shader_cache->RetrieveAsyncShaders();
      g_vertex_manager->OnEndFrame();

      // We invalidate the pipeline object at the start of the frame.
      // This is for the rare case where only a single pipeline configuration is used,
      // and hybrid ubershaders have compiled the specialized shader, but without any
      // state changes the specialized shader will not take over.
      g_vertex_manager->InvalidatePipelineObject();

      // Flush any outstanding EFB copies to RAM, in case the game is running at an uncapped frame
      // rate and not waiting for vblank. Otherwise, we'd end up with a huge list of pending copies.
      g_texture_cache->FlushEFBCopies();

      if (!is_duplicate_frame)
      {
        // Remove stale EFB/XFB copies.
        g_texture_cache->Cleanup(m_frame_count);
        Core::Callback_VideoCopiedToXFB(true);
      }

      // Handle any config changes, this gets propogated to the backend.
      if (g_ActiveConfig.bDirty)
      {
        g_ActiveConfig.bDirty = false;
        CheckForConfigChanges();
      }
      g_Config.iSaveTargetId = 0;

      EndUtilityDrawing();
    }
    else
    {
      Flush();
    }

    // Update our last xfb values
    m_last_xfb_width = (fb_width < 1 || fb_width > MAX_XFB_WIDTH) ? MAX_XFB_WIDTH : fb_width;
    m_last_xfb_height = (fb_height < 1 || fb_height > MAX_XFB_HEIGHT) ? MAX_XFB_HEIGHT : fb_height;
  }
  else
  {
    Flush();
  }
}

void Renderer::RenderXFBToScreen(const MathUtil::Rectangle<int>& target_rc,
                                 const AbstractTexture* source_texture,
                                 const MathUtil::Rectangle<int>& source_rc)
{
  m_post_processor->BlitFromTexture(target_rc, source_rc, source_texture, 0);
}

bool Renderer::IsFrameDumping()
{
  if (m_screenshot_request.IsSet())
    return true;

  if (SConfig::GetInstance().m_DumpFrames)
    return true;

  return false;
}

void Renderer::DumpCurrentFrame(const AbstractTexture* src_texture,
                                const MathUtil::Rectangle<int>& src_rect, u64 ticks)
{
  int source_width = src_rect.GetWidth();
  int source_height = src_rect.GetHeight();
  int target_width, target_height;
  if (!g_ActiveConfig.bInternalResolutionFrameDumps && !IsHeadless())
  {
    target_width = m_target_rectangle.GetWidth();
    target_height = m_target_rectangle.GetHeight();
  }
  else
  {
    std::tie(target_width, target_height) = CalculateOutputDimensions(source_width, source_height);
  }

  // We only need to render a copy if we need to stretch/scale the XFB copy.
  MathUtil::Rectangle<int> copy_rect = src_rect;
  if (source_width != target_width || source_height != target_height)
  {
    if (!CheckFrameDumpRenderTexture(target_width, target_height))
      return;

    ScaleTexture(m_frame_dump_render_framebuffer.get(), m_frame_dump_render_framebuffer->GetRect(),
                 src_texture, src_rect);
    src_texture = m_frame_dump_render_texture.get();
    copy_rect = src_texture->GetRect();
  }

  // Index 0 was just sent to FFMPEG dump. Swap with the second texture.
  if (m_frame_dump_readback_textures[0])
    std::swap(m_frame_dump_readback_textures[0], m_frame_dump_readback_textures[1]);

  if (!CheckFrameDumpReadbackTexture(target_width, target_height))
    return;

  m_frame_dump_readback_textures[0]->CopyFromTexture(src_texture, copy_rect, 0, 0,
                                                     m_frame_dump_readback_textures[0]->GetRect());
  m_last_frame_state = FrameDump::FetchState(ticks);
  m_last_frame_exported = true;
}

bool Renderer::CheckFrameDumpRenderTexture(u32 target_width, u32 target_height)
{
  // Ensure framebuffer exists (we lazily allocate it in case frame dumping isn't used).
  // Or, resize texture if it isn't large enough to accommodate the current frame.
  if (m_frame_dump_render_texture && m_frame_dump_render_texture->GetWidth() == target_width &&
      m_frame_dump_render_texture->GetHeight() == target_height)
  {
    return true;
  }

  // Recreate texture, but release before creating so we don't temporarily use twice the RAM.
  m_frame_dump_render_framebuffer.reset();
  m_frame_dump_render_texture.reset();
  m_frame_dump_render_texture =
      CreateTexture(TextureConfig(target_width, target_height, 1, 1, 1,
                                  AbstractTextureFormat::RGBA8, AbstractTextureFlag_RenderTarget));
  if (!m_frame_dump_render_texture)
  {
    PanicAlert("Failed to allocate frame dump render texture");
    return false;
  }
  m_frame_dump_render_framebuffer = CreateFramebuffer(m_frame_dump_render_texture.get(), nullptr);
  ASSERT(m_frame_dump_render_framebuffer);
  return true;
}

bool Renderer::CheckFrameDumpReadbackTexture(u32 target_width, u32 target_height)
{
  std::unique_ptr<AbstractStagingTexture>& rbtex = m_frame_dump_readback_textures[0];
  if (rbtex && rbtex->GetWidth() == target_width && rbtex->GetHeight() == target_height)
    return true;

  rbtex.reset();
  rbtex = CreateStagingTexture(
      StagingTextureType::Readback,
      TextureConfig(target_width, target_height, 1, 1, 1, AbstractTextureFormat::RGBA8, 0));
  if (!rbtex)
    return false;

  return true;
}

void Renderer::FlushFrameDump()
{
  if (!m_last_frame_exported)
    return;

  // Ensure the previously-queued frame was encoded.
  FinishFrameData();

  // Queue encoding of the last frame dumped.
  std::unique_ptr<AbstractStagingTexture>& rbtex = m_frame_dump_readback_textures[0];
  rbtex->Flush();
  if (rbtex->Map())
  {
    DumpFrameData(reinterpret_cast<u8*>(rbtex->GetMappedPointer()), rbtex->GetConfig().width,
                  rbtex->GetConfig().height, static_cast<int>(rbtex->GetMappedStride()),
                  m_last_frame_state);
    rbtex->Unmap();
  }

  m_last_frame_exported = false;

  // Shutdown frame dumping if it is no longer active.
  if (!IsFrameDumping())
    ShutdownFrameDumping();
}

void Renderer::ShutdownFrameDumping()
{
  // Ensure the last queued readback has been sent to the encoder.
  FlushFrameDump();

  if (!m_frame_dump_thread_running.IsSet())
    return;

  // Ensure previous frame has been encoded.
  FinishFrameData();

  // Wake thread up, and wait for it to exit.
  m_frame_dump_thread_running.Clear();
  m_frame_dump_start.Set();
  if (m_frame_dump_thread.joinable())
    m_frame_dump_thread.join();
  m_frame_dump_render_framebuffer.reset();
  m_frame_dump_render_texture.reset();
  for (auto& tex : m_frame_dump_readback_textures)
    tex.reset();
}

void Renderer::DumpFrameData(const u8* data, int w, int h, int stride,
                             const FrameDump::Frame& state)
{
  m_frame_dump_config = FrameDumpConfig{data, w, h, stride, state};

  if (!m_frame_dump_thread_running.IsSet())
  {
    if (m_frame_dump_thread.joinable())
      m_frame_dump_thread.join();
    m_frame_dump_thread_running.Set();
    m_frame_dump_thread = std::thread(&Renderer::RunFrameDumps, this);
  }

  // Wake worker thread up.
  m_frame_dump_start.Set();
  m_frame_dump_frame_running = true;
}

void Renderer::FinishFrameData()
{
  if (!m_frame_dump_frame_running)
    return;

  m_frame_dump_done.Wait();
  m_frame_dump_frame_running = false;
}

void Renderer::RunFrameDumps()
{
  Common::SetCurrentThreadName("FrameDumping");
  bool dump_to_ffmpeg = !g_ActiveConfig.bDumpFramesAsImages;
  bool frame_dump_started = false;

// If Dolphin was compiled without ffmpeg, we only support dumping to images.
#if !defined(HAVE_FFMPEG)
  if (dump_to_ffmpeg)
  {
    WARN_LOG(VIDEO, "FrameDump: Dolphin was not compiled with FFmpeg, using fallback option. "
                    "Frames will be saved as PNG images instead.");
    dump_to_ffmpeg = false;
  }
#endif

  while (true)
  {
    m_frame_dump_start.Wait();
    if (!m_frame_dump_thread_running.IsSet())
      break;

    auto config = m_frame_dump_config;

    // Save screenshot
    if (m_screenshot_request.TestAndClear())
    {
      std::lock_guard<std::mutex> lk(m_screenshot_lock);

      if (TextureToPng(config.data, config.stride, m_screenshot_name, config.width, config.height,
                       false))
        OSD::AddMessage("Screenshot saved to " + m_screenshot_name);

      // Reset settings
      m_screenshot_name.clear();
      m_screenshot_completed.Set();
    }

    if (SConfig::GetInstance().m_DumpFrames)
    {
      if (!frame_dump_started)
      {
        if (dump_to_ffmpeg)
          frame_dump_started = StartFrameDumpToFFMPEG(config);
        else
          frame_dump_started = StartFrameDumpToImage(config);

        // Stop frame dumping if we fail to start.
        if (!frame_dump_started)
          SConfig::GetInstance().m_DumpFrames = false;
      }

      // If we failed to start frame dumping, don't write a frame.
      if (frame_dump_started)
      {
        if (dump_to_ffmpeg)
          DumpFrameToFFMPEG(config);
        else
          DumpFrameToImage(config);
      }
    }

    m_frame_dump_done.Set();
  }

  if (frame_dump_started)
  {
    // No additional cleanup is needed when dumping to images.
    if (dump_to_ffmpeg)
      StopFrameDumpToFFMPEG();
  }
}

#if defined(HAVE_FFMPEG)

bool Renderer::StartFrameDumpToFFMPEG(const FrameDumpConfig& config)
{
  return FrameDump::Start(config.width, config.height);
}

void Renderer::DumpFrameToFFMPEG(const FrameDumpConfig& config)
{
  FrameDump::AddFrame(config.data, config.width, config.height, config.stride, config.state);
}

void Renderer::StopFrameDumpToFFMPEG()
{
  FrameDump::Stop();
}

#else

bool Renderer::StartFrameDumpToFFMPEG(const FrameDumpConfig& config)
{
  return false;
}

void Renderer::DumpFrameToFFMPEG(const FrameDumpConfig& config)
{
}

void Renderer::StopFrameDumpToFFMPEG()
{
}

#endif  // defined(HAVE_FFMPEG)

std::string Renderer::GetFrameDumpNextImageFileName() const
{
  return StringFromFormat("%sframedump_%u.png", File::GetUserPath(D_DUMPFRAMES_IDX).c_str(),
                          m_frame_dump_image_counter);
}

bool Renderer::StartFrameDumpToImage(const FrameDumpConfig& config)
{
  m_frame_dump_image_counter = 1;
  if (!SConfig::GetInstance().m_DumpFramesSilent)
  {
    // Only check for the presence of the first image to confirm overwriting.
    // A previous run will always have at least one image, and it's safe to assume that if the user
    // has allowed the first image to be overwritten, this will apply any remaining images as well.
    std::string filename = GetFrameDumpNextImageFileName();
    if (File::Exists(filename))
    {
      if (!AskYesNoT("Frame dump image(s) '%s' already exists. Overwrite?", filename.c_str()))
        return false;
    }
  }

  return true;
}

void Renderer::DumpFrameToImage(const FrameDumpConfig& config)
{
  std::string filename = GetFrameDumpNextImageFileName();
  TextureToPng(config.data, config.stride, filename, config.width, config.height, false);
  m_frame_dump_image_counter++;
}

bool Renderer::UseVertexDepthRange() const
{
  // We can't compute the depth range in the vertex shader if we don't support depth clamp.
  if (!g_ActiveConfig.backend_info.bSupportsDepthClamp)
    return false;

  // We need a full depth range if a ztexture is used.
  if (bpmem.ztex2.type != ZTEXTURE_DISABLE && !bpmem.zcontrol.early_ztest)
    return true;

  // If an inverted depth range is unsupported, we also need to check if the range is inverted.
  if (!g_ActiveConfig.backend_info.bSupportsReversedDepthRange && xfmem.viewport.zRange < 0.0f)
    return true;

  // If an oversized depth range or a ztexture is used, we need to calculate the depth range
  // in the vertex shader.
  return fabs(xfmem.viewport.zRange) > 16777215.0f || fabs(xfmem.viewport.farZ) > 16777215.0f;
}

std::unique_ptr<VideoCommon::AsyncShaderCompiler> Renderer::CreateAsyncShaderCompiler()
{
  return std::make_unique<VideoCommon::AsyncShaderCompiler>();
}
