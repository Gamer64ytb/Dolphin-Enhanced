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
GameFile sGameFile;
WiimoteAdapter sWiimoteAdapter;
CompressCallback sCompressCallback;
NetworkHelper sNetworkHelper;
ContentHandler sContentHandler;

void NativeLibrary::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/NativeLibrary");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  DisplayAlertMsg = env->GetStaticMethodID(clazz, "displayAlertMsg",
                                           "(Ljava/lang/String;Ljava/lang/String;Z)Z");
  RumbleOutputMethod = env->GetStaticMethodID(clazz, "rumble", "(ID)V");
  UpdateWindowSize = env->GetStaticMethodID(clazz, "updateWindowSize", "(II)V");
  BindSystemBack = env->GetStaticMethodID(clazz, "bindSystemBack", "(Ljava/lang/String;)V");
  GetEmulationContext = env->GetStaticMethodID(clazz, "getEmulationContext", "()Landroid/content/Context;");
}

void NativeLibrary::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void IniFile::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/model/IniFile");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  Pointer = env->GetFieldID(clazz, "pointer", "J");
}

void IniFile::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void GameFile::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/model/GameFile");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  Pointer = env->GetFieldID(clazz, "pointer", "J");
  Constructor = env->GetMethodID(clazz, "<init>", "(J)V");
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

void CompressCallback::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/CompressCallback");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  CompressCbRun = env->GetMethodID(clazz, "run", "(Ljava/lang/String;F)Z");
}

void CompressCallback::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void NetworkHelper::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/NetworkHelper");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  NetworkIpAddress = env->GetStaticMethodID(clazz, "GetNetworkIpAddress", "()I");
  NetworkPrefixLength = env->GetStaticMethodID(clazz, "GetNetworkPrefixLength", "()I");
  NetworkGateway = env->GetStaticMethodID(clazz, "GetNetworkGateway", "()I");
}

void NetworkHelper::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  Clazz = nullptr;
}

void ContentHandler::OnLoad(JNIEnv* env)
{
  jclass clazz = env->FindClass("org/dolphinemu/dolphinemu/utils/ContentHandler");
  jclass stringClazz = env->FindClass("java/lang/String");
  Clazz = reinterpret_cast<jclass>(env->NewGlobalRef(clazz));
  StringClazz = reinterpret_cast<jclass>(env->NewGlobalRef(stringClazz));
  OpenFd = env->GetStaticMethodID(clazz, "openFd", "(Ljava/lang/String;Ljava/lang/String;)I");
  Delete = env->GetStaticMethodID(clazz, "delete", "(Ljava/lang/String;)Z");
  GetSizeAndIsDirectory = env->GetStaticMethodID(clazz, "getSizeAndIsDirectory", "(Ljava/lang/String;)J");
  GetDisplayName = env->GetStaticMethodID(clazz, "getDisplayName", "(Ljava/lang/String;)Ljava/lang/String;");
  GetChildNames = env->GetStaticMethodID(clazz, "getChildNames", "(Ljava/lang/String;Z)[Ljava/lang/String;");
  DoFileSearch = env->GetStaticMethodID(clazz, "doFileSearch", "(Ljava/lang/String;[Ljava/lang/String;Z)[Ljava/lang/String;");
}

void ContentHandler::OnUnload(JNIEnv* env)
{
  env->DeleteGlobalRef(Clazz);
  env->DeleteGlobalRef(StringClazz);
  Clazz = nullptr;
  StringClazz = nullptr;
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

jint JNI_OnLoad(JavaVM* vm, void*)
{
  s_java_vm = vm;

  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK)
    return JNI_ERR;

  IDCache::sNativeLibrary.OnLoad(env);
  IDCache::sIniFile.OnLoad(env);
  IDCache::sGameFile.OnLoad(env);
  IDCache::sWiimoteAdapter.OnLoad(env);
  IDCache::sCompressCallback.OnLoad(env);
  IDCache::sNetworkHelper.OnLoad(env);
  IDCache::sContentHandler.OnLoad(env);

  return JNI_VERSION;
}

void JNI_OnUnload(JavaVM* vm, void*)
{
  JNIEnv* env;
  if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION) != JNI_OK)
    return;

  UICommon::Shutdown();

  IDCache::sNativeLibrary.OnUnload(env);
  IDCache::sIniFile.OnUnload(env);
  IDCache::sGameFile.OnUnload(env);
  IDCache::sWiimoteAdapter.OnUnload(env);
  IDCache::sCompressCallback.OnUnload(env);
  IDCache::sNetworkHelper.OnUnload(env);
  IDCache::sContentHandler.OnUnload(env);
}

#ifdef __cplusplus
}
#endif
