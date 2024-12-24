// Copyright 2018 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "jni/AndroidCommon/IDCache.h"
#include "jni/AndroidCommon/AndroidCommon.h"

#include <algorithm>
#include <ios>
#include <string>
#include <vector>

#include <jni.h>

#include "Common/Assert.h"
#include "Common/StringUtil.h"

std::string GetJString(JNIEnv* env, jstring jstr)
{
  std::string result;
  if (!jstr)
    return result;

  const char* s = env->GetStringUTFChars(jstr, nullptr);
  result = s;
  env->ReleaseStringUTFChars(jstr, s);
  env->DeleteLocalRef(jstr);
  return result;
}

jstring ToJString(JNIEnv* env, const std::string& str)
{
  return env->NewStringUTF(str.c_str());
}

std::vector<std::string> JStringArrayToVector(JNIEnv* env, jobjectArray array)
{
  const jsize size = env->GetArrayLength(array);
  std::vector<std::string> result;
  for (jsize i = 0; i < size; ++i)
    result.push_back(GetJString(env, (jstring)env->GetObjectArrayElement(array, i)));

  return result;
}

jobjectArray JStringArrayFromVector(JNIEnv* env, std::vector<std::string> vector)
{
  jobjectArray result = env->NewObjectArray(vector.size(), IDCache::sContentHandler.StringClazz, nullptr);
  for (jsize i = 0; i < vector.size(); ++i)
    env->SetObjectArrayElement(result, i, ToJString(env, vector[i]));
  return result;
}

bool IsPathAndroidContent(const std::string& uri)
{
  return StringBeginsWith(uri, "content://");
}

std::string OpenModeToAndroid(std::string mode)
{
  // The 'b' specifier is not supported. Since we're on POSIX, it's fine to just skip it.
  mode.erase(std::remove(mode.begin(), mode.end(), 'b'));

  if (mode == "r+")
    mode = "rw";
  else if (mode == "w+")
    mode = "rwt";
  else if (mode == "a+")
    mode = "rwa";
  else if (mode == "a")
    mode = "wa";

  return mode;
}

std::string OpenModeToAndroid(std::ios_base::openmode mode)
{
  std::string result;

  if (mode & std::ios_base::in)
    result += 'r';

  if (mode & (std::ios_base::out | std::ios_base::app))
    result += 'w';

  if (mode & std::ios_base::app)
    result += 'a';

  constexpr std::ios_base::openmode t = std::ios_base::in | std::ios_base::trunc;
  if ((mode & t) == t)
    result += 't';

  // The 'b' specifier is not supported. Since we're on POSIX, it's fine to just skip it.

  return result;
}

int OpenAndroidContent(const std::string& uri, const std::string& mode)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticIntMethod(IDCache::sContentHandler.Clazz,
                                  IDCache::sContentHandler.OpenFd, ToJString(env, uri),
                                  ToJString(env, mode));
}

bool DeleteAndroidContent(const std::string& uri)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticBooleanMethod(IDCache::sContentHandler.Clazz,
                                      IDCache::sContentHandler.Delete, ToJString(env, uri));
}

jlong GetAndroidContentSizeAndIsDirectory(const std::string& uri)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticLongMethod(IDCache::sContentHandler.Clazz,
                                   IDCache::sContentHandler.GetSizeAndIsDirectory,
                                   ToJString(env, uri));
}

std::string GetAndroidContentDisplayName(const std::string& uri)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  jobject display_name =
      env->CallStaticObjectMethod(IDCache::sContentHandler.Clazz,
                                  IDCache::sContentHandler.GetDisplayName, ToJString(env, uri));
  return display_name ? GetJString(env, reinterpret_cast<jstring>(display_name)) : "";
}

std::vector<std::string> GetAndroidContentChildNames(const std::string& uri)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  jobject children = env->CallStaticObjectMethod(IDCache::sContentHandler.Clazz,
                                                 IDCache::sContentHandler.GetChildNames,
                                                 ToJString(env, uri), false);
  return JStringArrayToVector(env, reinterpret_cast<jobjectArray>(children));
}

std::vector<std::string> DoFileSearchAndroidContent(const std::string& directory,
                                                    const std::vector<std::string>& extensions,
                                                    bool recursive)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  jobject result = env->CallStaticObjectMethod(
      IDCache::sContentHandler.Clazz, IDCache::sContentHandler.DoFileSearch,
      ToJString(env, directory), JStringArrayFromVector(env, extensions), recursive);
  return JStringArrayToVector(env, reinterpret_cast<jobjectArray>(result));
}

int GetNetworkIpAddress()
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticIntMethod(IDCache::sNetworkHelper.Clazz,
                                  IDCache::sNetworkHelper.NetworkIpAddress);
}

int GetNetworkPrefixLength()
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticIntMethod(IDCache::sNetworkHelper.Clazz,
                                  IDCache::sNetworkHelper.NetworkPrefixLength);
}

int GetNetworkGateway()
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticIntMethod(IDCache::sNetworkHelper.Clazz,
                                  IDCache::sNetworkHelper.NetworkGateway);
}
