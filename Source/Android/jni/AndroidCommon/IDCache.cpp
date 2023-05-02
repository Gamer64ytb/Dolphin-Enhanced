// Copyright 2018 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "jni/AndroidCommon/IDCache.h"
#include "UICommon/UICommon.h"
#include "Core/HW/WiimoteReal/WiimoteReal.h"

#include <jni.h>

static constexpr jint JNI_VERSION = JNI_VERSION_1_6;
static JavaVM* s_java_vm;

namespace IDCache
{
NativeLibrary sNativeLibrary;
IniFile sIniFile;
SafHandler sSafHandler;
CompressCallback sCompressCallback;
GameFile sGameFile;
WiimoteAdapter sWiimoteAdapter;

void NativeLibrary::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/NativeLibrary");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  DisplayAlertMsg = env->GetStaticMethodID(Clazz, "displayAlertMsg",
                                           "(Ljava/lang/String;Ljava/lang/String;Z)Z");
  RumbleOutputMethod = env->GetStaticMethodID(Clazz, "rumble", "(ID)V");
  UpdateWindowSize = env->GetStaticMethodID(Clazz, "updateWindowSize", "(II)V");
  BindSystemBack = env->GetStaticMethodID(Clazz, "bindSystemBack", "(Ljava/lang/String;)V");
  GetEmulationContext = env->GetStaticMethodID(Clazz, "getEmulationContext", "()Landroid/content/Context;");
}

void NativeLibrary::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void IniFile::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/IniFile");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  jclass section_clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/IniFile$Section");
  SectionClazz = reinterpret_cast<jclass>(env->NewGlobalRef(section_clazz));
  Pointer = env->GetFieldID(clazz, "mPointer", "J");
  SectionPointer = env->GetFieldID(section_clazz, "mPointer", "J");
  SectionConstructor = env->GetMethodID(section_clazz, "<init>", "(Lorg/dolphinemu/dolphinemu/utils/IniFile;J)V");
}

void IniFile::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  env->DeleteGlobalRef(SectionClazz);
  Clazz = nullptr;
  SectionClazz = nullptr;
}

void SafHandler::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/SafHandler");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  jclass string_clazz = env->FindClass("java/lang/String");
  StringClazz = reinterpret_cast<jclass>(env->NewGlobalRef(string_clazz));
  OpenFd = env->GetStaticMethodID(Clazz, "openFd","(Ljava/lang/String;Ljava/lang/String;)I");
  Delete = env->GetStaticMethodID(Clazz, "delete", "(Ljava/lang/String;)Z");
  GetSizeAndIsDir = env->GetStaticMethodID(Clazz, "getSizeAndIsDirectory", "(Ljava/lang/String;)J");
  GetDisplayName = env->GetStaticMethodID(Clazz, "getDisplayName", "(Ljava/lang/String;)Ljava/lang/String;");
  GetChildNames = env->GetStaticMethodID(Clazz, "getChildNames", "(Ljava/lang/String;Z)[Ljava/lang/String;");
  DoFileSearch = env->GetStaticMethodID(Clazz, "doFileSearch", "(Ljava/lang/String;[Ljava/lang/String;Z)[Ljava/lang/String;");
}

void SafHandler::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  env->DeleteGlobalRef(StringClazz);
  Clazz = nullptr;
  StringClazz = nullptr;
}

void CompressCallback::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/CompressCallback");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  Run = env->GetMethodID(Clazz, "run", "(Ljava/lang/String;F)Z");
}

void CompressCallback::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void GameFile::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/model/GameFile");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  Pointer = env->GetFieldID(Clazz, "mPointer", "J");
  Constructor = env->GetMethodID(Clazz, "<init>", "(J)V");
}

void GameFile::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void WiimoteAdapter::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/Java_WiimoteAdapter");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
}

void WiimoteAdapter::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

JNIEnv* GetEnvForThread()
{
  thread_local static struct OwnedEnv
  {
    OwnedEnv()
    {
      status = s_java_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
      if (status == JNI_EDETACHED)
        s_java_vm->AttachCurrentThread(&env, nullptr);
    }

    ~OwnedEnv()
    {
      if (status == JNI_EDETACHED)
        s_java_vm->DetachCurrentThread();
    }

    int status;
    JNIEnv* env = nullptr;
  } owned;
  return owned.env;
}

}  // namespace IDCache

#ifdef __cplusplus
extern "C" {
#endif

jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
  s_java_vm = vm;

  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK)
    return JNI_ERR;

  IDCache::sNativeLibrary.OnLoad(env);
  IDCache::sIniFile.OnLoad(env);
  IDCache::sSafHandler.OnLoad(env);
  IDCache::sCompressCallback.OnLoad(env);
  IDCache::sGameFile.OnLoad(env);
  IDCache::sWiimoteAdapter.OnLoad(env);

  return JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void* reserved)
{
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK)
    return;

  UICommon::Shutdown();

  IDCache::sNativeLibrary.OnUnload(env);
  IDCache::sIniFile.OnUnload(env);
  IDCache::sSafHandler.OnUnload(env);
  IDCache::sCompressCallback.OnUnload(env);
  IDCache::sGameFile.OnUnload(env);
  IDCache::sWiimoteAdapter.OnUnload(env);
}

#ifdef __cplusplus
}
#endif
