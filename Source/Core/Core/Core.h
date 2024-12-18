// Copyright 2008 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

// Core

// The external interface to the emulator core. Plus some extras.
// This is another part of the emu that needs cleaning - Core.cpp really has
// too much random junk inside.

#pragma once

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "Common/CommonTypes.h"

struct BootParameters;
struct WindowSystemInfo;

namespace Core
{
bool GetIsThrottlerTempDisabled();
void SetIsThrottlerTempDisabled(bool disable);

void Callback_VideoCopiedToXFB(bool video_update);

enum class State
{
  Uninitialized,
  Paused,
  Running,
  Stopping,
  Starting,
};

bool Init(std::unique_ptr<BootParameters> boot, const WindowSystemInfo& wsi);
void Stop();
void Shutdown();

void DeclareAsCPUThread();
void UndeclareAsCPUThread();

std::string StopMessage(bool, const std::string&);

bool IsRunning();
bool IsRunningAndStarted();       // is running and the CPU loop has been entered
bool IsRunningInCurrentThread();  // this tells us whether we are running in the CPU thread.
bool IsCPUThread();               // this tells us whether we are the CPU thread.
bool IsGPUThread();

bool WantsDeterminism();

// [NOT THREADSAFE] For use by Host only
void SetState(State state);
State GetState();

void SaveScreenShot(bool wait_for_completion = false);
void SaveScreenShot(const std::string& name, bool wait_for_completion = false);

void Callback_WiimoteInterruptChannel(int number, u16 channel_id, const u8* data, u32 size);

// This displays messages in a user-visible way.
void DisplayMessage(const std::string& message, int time_in_ms);

void FrameUpdateOnCPUThread();
void OnFrameEnd();

void VideoThrottle();
void RequestRefreshInfo();

struct PerformanceStatistics
{
  float FPS;
  float VPS;
  float Speed;
};

const PerformanceStatistics& GetPerformanceStatistics();

void UpdateTitle();

// Run a function as the CPU thread.
//
// If called from the Host thread, the CPU thread is paused and the current thread temporarily
// becomes the CPU thread while running the function.
// If called from the CPU thread, the function will be run directly.
//
// This should only be called from the CPU thread or the host thread.
void RunAsCPUThread(std::function<void()> function);

// for calling back into UI code without introducing a dependency on it in core
using StateChangedCallbackFunc = std::function<void(Core::State)>;
void SetOnStateChangedCallback(StateChangedCallbackFunc callback);

// Run on the Host thread when the factors change. [NOT THREADSAFE]
void UpdateWantDeterminism(bool initial = false);

// Queue an arbitrary function to asynchronously run once on the Host thread later.
// Threadsafe. Can be called by any thread, including the Host itself.
// Jobs will be executed in RELATIVE order. If you queue 2 jobs from the same thread
// then they will be executed in the order they were queued; however, there is no
// global order guarantee across threads - jobs from other threads may execute in
// between.
// NOTE: Make sure the jobs check the global state instead of assuming everything is
//   still the same as when the job was queued.
// NOTE: Jobs that are not set to run during stop will be discarded instead.
void QueueHostJob(std::function<void()> job, bool run_during_stop = false);

// Should be called periodically by the Host to run pending jobs.
// WMUserJobDispatch will be sent when something is added to the queue.
void HostDispatchJobs();

void DoFrameStep();

}  // namespace Core
