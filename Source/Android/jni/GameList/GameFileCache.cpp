// Copyright 2018 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include <memory>
#include <vector>

#include <jni.h>

#include "UICommon/GameFile.h"
#include "UICommon/GameFileCache.h"
#include "jni/AndroidCommon/AndroidCommon.h"
#include "jni/AndroidCommon/IDCache.h"
#include "jni/GameList/GameFile.h"

static std::shared_ptr<UICommon::GameFileCache> g_game_file_cache;

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT void JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_init(JNIEnv*, jclass)
{
  g_game_file_cache = std::make_shared<UICommon::GameFileCache>();
}

JNIEXPORT jobjectArray JNICALL
Java_org_dolphinemu_dolphinemu_model_GameFileCache_getAllGames(JNIEnv* env, jobject)
{
  const jobjectArray array = env->NewObjectArray(static_cast<jsize>(g_game_file_cache->GetSize()),
                                                 IDCache::sGameFile.Clazz, nullptr);
  jsize i = 0;
  g_game_file_cache->ForEach([env, array, &i](const auto& game_file) {
    env->SetObjectArrayElement(array, i++, GameFileToJava(env, game_file.get()));
  });
  return array;
}

JNIEXPORT jobject JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_addOrGet(JNIEnv* env, jobject,
                                                          jstring path)
{
  bool cache_changed = false;
  return GameFileToJava(env, g_game_file_cache->AddOrGet(GetJString(env, path), &cache_changed).get());
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_update(
    JNIEnv* env, jobject, jobjectArray folder_paths)
{
  return g_game_file_cache->Update(UICommon::FindAllGamePaths(JStringArrayToVector(env, folder_paths), false));
}

JNIEXPORT jboolean JNICALL
Java_org_dolphinemu_dolphinemu_model_GameFileCache_updateAdditionalMetadata(JNIEnv*, jobject)
{
  return g_game_file_cache->UpdateAdditionalMetadata();
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_load(JNIEnv*, jobject)
{
  return g_game_file_cache->Load();
}

JNIEXPORT jboolean JNICALL Java_org_dolphinemu_dolphinemu_model_GameFileCache_save(JNIEnv*, jobject)
{
  return g_game_file_cache->Save();
}

#ifdef __cplusplus
}
#endif
