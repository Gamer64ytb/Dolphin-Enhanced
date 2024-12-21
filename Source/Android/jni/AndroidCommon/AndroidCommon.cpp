// Copyright 2018 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "jni/AndroidCommon/IDCache.h"
#include "jni/AndroidCommon/AndroidCommon.h"

#include <string>
#include <vector>

#include <jni.h>

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

int OpenAndroidContent(const std::string& uri, const std::string& mode)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  const jint fd = env->CallStaticIntMethod(IDCache::sContentHandler.Clazz,
                                           IDCache::sContentHandler.OpenFd, ToJString(env, uri),
                                           ToJString(env, mode));

  // We can get an IllegalArgumentException when passing an invalid mode
  if (env->ExceptionCheck())
  {
    env->ExceptionDescribe();
    abort();
  }

  return fd;
}

bool DeleteAndroidContent(const std::string& uri)
{
  JNIEnv* env = IDCache::GetEnvForThread();
  return env->CallStaticBooleanMethod(IDCache::sContentHandler.Clazz,
                                      IDCache::sContentHandler.Delete, ToJString(env, uri));
}
