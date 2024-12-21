// Copyright 2018 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <jni.h>

namespace IDCache
{

struct NativeLibrary
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
  jmethodID DisplayAlertMsg;
  jmethodID RumbleOutputMethod;
  jmethodID UpdateWindowSize;
  jmethodID BindSystemBack;
  jmethodID GetEmulationContext;
};

struct IniFile
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
  jfieldID Pointer;
  jclass SectionClazz;
  jfieldID SectionPointer;
  jmethodID SectionConstructor;
};

struct GameFile
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
  jfieldID Pointer;
  jmethodID Constructor;
};

struct WiimoteAdapter
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
};

struct CompressCallback
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
  jmethodID CompressCbRun;
};

struct ContentHandler
{
  void OnLoad(JNIEnv* env);
  void OnUnload(JNIEnv* env);

  jclass Clazz;
  jmethodID OpenFd;
  jmethodID Delete;
};

JNIEnv* GetEnvForThread();

extern NativeLibrary sNativeLibrary;
extern IniFile sIniFile;
extern GameFile sGameFile;
extern WiimoteAdapter sWiimoteAdapter;
extern CompressCallback sCompressCallback;
extern ContentHandler sContentHandler;

}  // namespace IDCache
