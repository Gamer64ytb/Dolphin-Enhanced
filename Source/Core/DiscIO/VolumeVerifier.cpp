// Copyright 2019 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "DiscIO/VolumeVerifier.h"

#include <algorithm>
#include <cassert>
#include <cinttypes>
#include <future>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <unordered_set>

#include <mbedtls/md5.h>
#include <mbedtls/sha1.h>
#include <minizip/unzip.h>
#include <pugixml.hpp>
#include <zlib.h>

#include "Common/Align.h"
#include "Common/Assert.h"
#include "Common/CommonPaths.h"
#include "Common/CommonTypes.h"
#include "Common/File.h"
#include "Common/FileUtil.h"
#include "Common/HttpRequest.h"
#include "Common/Logging/Log.h"
#include "Common/MinizipUtil.h"
#include "Common/MsgHandler.h"
#include "Common/ScopeGuard.h"
#include "Common/StringUtil.h"
#include "Common/Swap.h"
#include "Common/Version.h"
#include "Core/IOS/Device.h"
#include "Core/IOS/ES/ES.h"
#include "Core/IOS/ES/Formats.h"
#include "Core/IOS/IOS.h"
#include "Core/IOS/IOSC.h"
#include "DiscIO/Blob.h"
#include "DiscIO/DiscExtractor.h"
#include "DiscIO/DiscScrubber.h"
#include "DiscIO/Enums.h"
#include "DiscIO/Filesystem.h"
#include "DiscIO/Volume.h"
#include "DiscIO/VolumeWii.h"

namespace DiscIO
{
RedumpVerifier::DownloadState RedumpVerifier::m_gc_download_state;
RedumpVerifier::DownloadState RedumpVerifier::m_wii_download_state;

void RedumpVerifier::Start(const Volume& volume)
{
  // We use GetGameTDBID instead of GetGameID so that Datel discs will be represented by an empty
  // string, which matches Redump not having any serials for Datel discs.
  m_game_id = volume.GetGameTDBID();
  if (m_game_id.size() > 4)
    m_game_id = m_game_id.substr(0, 4);

  m_revision = volume.GetRevision().value_or(0);
  m_disc_number = volume.GetDiscNumber().value_or(0);
  m_size = volume.GetSize();

  const DiscIO::Platform platform = volume.GetVolumeType();

  m_future = std::async(std::launch::async, [this, platform]() -> std::vector<PotentialMatch> {
    std::string system;
    DownloadState* download_state;
    switch (platform)
    {
    case Platform::GameCubeDisc:
      system = "gc";
      download_state = &m_gc_download_state;
      break;

    case Platform::WiiDisc:
      system = "wii";
      download_state = &m_wii_download_state;
      break;

    default:
      m_result.status = Status::Error;
      return {};
    }

    {
      std::lock_guard lk(download_state->mutex);
      download_state->status = DownloadDatfile(system, download_state->status);
    }

    switch (download_state->status)
    {
    case DownloadStatus::FailButOldCacheAvailable:
      ERROR_LOG(DISCIO, "Failed to fetch data from Redump.org, using old cached data instead");
      [[fallthrough]];
    case DownloadStatus::Success:
      return ScanDatfile(ReadDatfile(system), system);

    case DownloadStatus::SystemNotAvailable:
      m_result = {Status::Error, Common::GetStringT("Wii data is not public yet")};
      return {};

    case DownloadStatus::Fail:
    default:
      m_result = {Status::Error, Common::GetStringT("Failed to connect to Redump.org")};
      return {};
    }
  });
}

static std::string GetPathForSystem(const std::string& system)
{
  return File::GetUserPath(D_REDUMPCACHE_IDX) + DIR_SEP + system + ".zip";
}

RedumpVerifier::DownloadStatus RedumpVerifier::DownloadDatfile(const std::string& system,
                                                               DownloadStatus old_status)
{
  if (old_status == DownloadStatus::Success || old_status == DownloadStatus::SystemNotAvailable)
    return old_status;

  Common::HttpRequest request;

  const std::optional<std::vector<u8>> result =
      request.Get("http://redump.org/datfile/" + system + "/serial,version",
                  {{"User-Agent", Common::scm_rev_str}});

  const std::string output_path = GetPathForSystem(system);

  if (!result)
  {
    return File::Exists(output_path) ? DownloadStatus::FailButOldCacheAvailable :
                                       DownloadStatus::Fail;
  }

  if (result->size() > 1 && (*result)[0] == '<' && (*result)[1] == '!')
  {
    // This is an HTML page, not a zip file like we want

    if (File::Exists(output_path))
      return DownloadStatus::FailButOldCacheAvailable;

    const std::string system_not_available_message = "System \"" + system + "\" doesn't exist.";
    const bool system_not_available_match =
        result->end() != std::search(result->begin(), result->end(),
                                     system_not_available_message.begin(),
                                     system_not_available_message.end());
    return system_not_available_match ? DownloadStatus::SystemNotAvailable : DownloadStatus::Fail;
  }

  File::CreateFullPath(output_path);
  if (!File::IOFile(output_path, "wb").WriteBytes(result->data(), result->size()))
    ERROR_LOG(DISCIO, "Failed to write downloaded datfile to %s", output_path.c_str());
  return DownloadStatus::Success;
}

std::vector<u8> RedumpVerifier::ReadDatfile(const std::string& system)
{
  unzFile file = unzOpen(GetPathForSystem(system).c_str());
  if (!file)
    return {};

  Common::ScopeGuard file_guard{[&] { unzClose(file); }};

  // Check that the zip file contains exactly one file
  if (unzGoToFirstFile(file) != UNZ_OK)
    return {};
  if (unzGoToNextFile(file) != UNZ_END_OF_LIST_OF_FILE)
    return {};

  // Read the file
  if (unzGoToFirstFile(file) != UNZ_OK)
    return {};
  unz_file_info file_info;
  unzGetCurrentFileInfo(file, &file_info, nullptr, 0, nullptr, 0, nullptr, 0);
  std::vector<u8> data(file_info.uncompressed_size);
  if (!Common::ReadFileFromZip(file, &data))
    return {};

  return data;
}

static u8 ParseHexDigit(char c)
{
  if (c < '0')
    return 0;  // Error

  if (c >= 'a')
    c -= 'a' - 'A';
  if (c >= 'A')
    c -= 'A' - ('9' + 1);
  c -= '0';

  if (c >= 0x10)
    return 0;  // Error

  return c;
}

static std::vector<u8> ParseHash(const char* str)
{
  std::vector<u8> hash;
  while (str[0] && str[1])
  {
    hash.push_back(static_cast<u8>(ParseHexDigit(str[0]) * 0x10 + ParseHexDigit(str[1])));
    str += 2;
  }
  return hash;
}

std::vector<RedumpVerifier::PotentialMatch> RedumpVerifier::ScanDatfile(const std::vector<u8>& data,
                                                                        const std::string& system)
{
  pugi::xml_document doc;
  if (!doc.load_buffer(data.data(), data.size()))
  {
    m_result = {Status::Error, Common::GetStringT("Failed to parse Redump.org data")};
    return {};
  }

  std::vector<PotentialMatch> potential_matches;
  bool serials_exist = false;
  bool versions_exist = false;
  const pugi::xml_node datafile = doc.child("datafile");
  for (const pugi::xml_node game : datafile.children("game"))
  {
    std::string version_string = game.child("version").text().as_string();
    if (!version_string.empty())
      versions_exist = true;

    // Strip out prefix (e.g. "v1.02" -> "02", "Rev 2" -> "2")
    const size_t last_non_numeric = version_string.find_last_not_of("0123456789");
    if (last_non_numeric != std::string::npos)
      version_string = version_string.substr(last_non_numeric + 1);

    const int version = version_string.empty() ? 0 : std::stoi(version_string);

    // The revisions for Korean GameCube games whose four-char game IDs end in E are numbered from
    // 0x30 in ring codes and in disc headers, but Redump switched to numbering them from 0 in 2019.
    if (version % 0x30 != m_revision % 0x30)
      continue;

    const std::string serials = game.child("serial").text().as_string();
    if (!serials.empty())
      serials_exist = true;
    if (serials.empty() || StringBeginsWith(serials, "DS"))
    {
      // GC Datel discs have no serials in Redump, Wii Datel discs have serials like "DS000101"
      if (!m_game_id.empty())
        continue;  // Non-empty m_game_id means we're verifying a non-Datel disc
    }
    else
    {
      bool serial_match_found = false;

      // If a disc has multiple possible serials, they are delimited with ", ". We want to loop
      // through all the serials until we find a match, because even though they normally only
      // differ in the region code at the end (which we don't care about), there is an edge case
      // disc with the game ID "G96P" and the serial "DL-DOL-D96P-EUR, DL-DOL-G96P-EUR".
      for (const std::string& serial_str : SplitString(serials, ','))
      {
        const std::string_view serial = StripSpaces(serial_str);

        // Skip the prefix, normally either "DL-DOL-" or "RVL-" (depending on the console),
        // but there are some exceptions like the "RVLE-SBSE-USA-B0" serial.
        const size_t first_dash = serial.find_first_of('-', 3);
        const size_t game_id_start =
            first_dash == std::string::npos ? std::string::npos : first_dash + 1;

        if (game_id_start == std::string::npos || serial.size() < game_id_start + 4)
        {
          ERROR_LOG(DISCIO, "Invalid serial in redump datfile: %s", serial_str.c_str());
          continue;
        }

        const std::string_view game_id = serial.substr(game_id_start, 4);
        if (game_id != m_game_id)
          continue;

        u8 disc_number = 0;
        if (serial.size() > game_id_start + 5 && serial[game_id_start + 5] >= '0' &&
            serial[game_id_start + 5] <= '9')
        {
          disc_number = serial[game_id_start + 5] - '0';
        }
        if (disc_number != m_disc_number)
          continue;

        serial_match_found = true;
        break;
      }
      if (!serial_match_found)
        continue;
    }

    PotentialMatch& potential_match = potential_matches.emplace_back();
    const pugi::xml_node rom = game.child("rom");
    potential_match.size = rom.attribute("size").as_ullong();
    potential_match.hashes.crc32 = ParseHash(rom.attribute("crc").value());
    potential_match.hashes.md5 = ParseHash(rom.attribute("md5").value());
    potential_match.hashes.sha1 = ParseHash(rom.attribute("sha1").value());
  }

  if (!serials_exist || !versions_exist)
  {
    // If we reach this, the user has most likely downloaded a datfile manually,
    // so show a panic alert rather than just using ERROR_LOG

    // i18n: "Serial" refers to serial numbers, e.g. RVL-RSBE-USA
    PanicAlertT("Serial and/or version data is missing from %s", GetPathForSystem(system).c_str());
    m_result = {Status::Error, Common::GetStringT("Failed to parse Redump.org data")};
    return {};
  }

  return potential_matches;
}

static bool HashesMatch(const std::vector<u8>& calculated, const std::vector<u8>& expected)
{
  return calculated.empty() || calculated == expected;
}

RedumpVerifier::Result RedumpVerifier::Finish(const Hashes<std::vector<u8>>& hashes)
{
  if (m_result.status == Status::Error)
    return m_result;

  if (hashes.crc32.empty() && hashes.md5.empty() && hashes.sha1.empty())
    return m_result;

  const std::vector<PotentialMatch> potential_matches = m_future.get();
  for (PotentialMatch p : potential_matches)
  {
    if (HashesMatch(hashes.crc32, p.hashes.crc32) && HashesMatch(hashes.md5, p.hashes.md5) &&
        HashesMatch(hashes.sha1, p.hashes.sha1) && m_size == p.size)
    {
      return {Status::GoodDump, Common::GetStringT("Good dump")};
    }
  }

  // We only return bad dump if there's a disc that we know this dump should match but that doesn't
  // match. For disc without IDs (i.e. Datel discs), we don't have a good way of knowing whether we
  // have a bad dump or just a dump that isn't in Redump, so we always pick unknown instead of bad
  // dump for those to be on the safe side. (Besides, it's possible to dump a Datel disc correctly
  // and have it not match Redump if you don't use the same replacement value for bad sectors.)
  if (!potential_matches.empty() && !m_game_id.empty())
    return {Status::BadDump, Common::GetStringT("Bad dump")};

  return {Status::Unknown, Common::GetStringT("Unknown disc")};
}

constexpr u64 MINI_DVD_SIZE = 1459978240;  // GameCube
constexpr u64 SL_DVD_SIZE = 4699979776;    // Wii retail
constexpr u64 SL_DVD_R_SIZE = 4707319808;  // Wii RVT-R
constexpr u64 DL_DVD_SIZE = 8511160320;    // Wii retail
constexpr u64 DL_DVD_R_SIZE = 8543666176;  // Wii RVT-R

constexpr u64 BLOCK_SIZE = 0x20000;

VolumeVerifier::VolumeVerifier(const Volume& volume, bool redump_verification,
                               Hashes<bool> hashes_to_calculate)
    : m_volume(volume), m_redump_verification(redump_verification),
      m_hashes_to_calculate(hashes_to_calculate),
      m_calculating_any_hash(hashes_to_calculate.crc32 || hashes_to_calculate.md5 ||
                             hashes_to_calculate.sha1),
      m_max_progress(volume.GetSize())
{
  if (!m_calculating_any_hash)
    m_redump_verification = false;
}

VolumeVerifier::~VolumeVerifier() = default;

void VolumeVerifier::Start()
{
  ASSERT(!m_started);
  m_started = true;

  if (m_redump_verification)
    m_redump_verifier.Start(m_volume);

  m_is_tgc = m_volume.GetBlobType() == BlobType::TGC;
  m_is_datel = IsDisc(m_volume.GetVolumeType()) &&
               !GetBootDOLOffset(m_volume, m_volume.GetGamePartition()).has_value();
  m_is_not_retail =
      (m_volume.GetVolumeType() == Platform::WiiDisc && !m_volume.IsEncryptedAndHashed()) ||
      IsDebugSigned();

  if (m_volume.GetVolumeType() == Platform::WiiWAD)
    CheckCorrectlySigned(PARTITION_NONE, Common::GetStringT("This title is not correctly signed."));
  CheckDiscSize(CheckPartitions());
  CheckMisc();

  SetUpHashing();
}

std::vector<Partition> VolumeVerifier::CheckPartitions()
{
  if (m_volume.GetVolumeType() == Platform::WiiWAD)
    return {};

  const std::vector<Partition> partitions = m_volume.GetPartitions();
  if (partitions.empty())
  {
    if (!m_volume.GetFileSystem(m_volume.GetGamePartition()))
    {
      AddProblem(Severity::High,
                 Common::GetStringT("The filesystem is invalid or could not be read."));
      return {};
    }
    return {m_volume.GetGamePartition()};
  }

  std::optional<u32> partitions_in_first_table = m_volume.ReadSwapped<u32>(0x40000, PARTITION_NONE);
  if (partitions_in_first_table && *partitions_in_first_table > 8)
  {
    // Not sure if 8 actually is the limit, but there certainly aren't any discs
    // released that have as many partitions as 8 in the first partition table.
    // The only game that has that many partitions in total is Super Smash Bros. Brawl,
    // and that game places all partitions other than UPDATE and DATA in the second table.
    AddProblem(Severity::Low,
               Common::GetStringT("There are too many partitions in the first partition table."));
  }

  std::vector<u32> types;
  for (const Partition& partition : partitions)
  {
    const std::optional<u32> type = m_volume.GetPartitionType(partition);
    if (type)
      types.emplace_back(*type);
  }

  if (std::find(types.cbegin(), types.cend(), PARTITION_UPDATE) == types.cend())
    AddProblem(Severity::Low, Common::GetStringT("The update partition is missing."));

  const bool has_data_partition =
      std::find(types.cbegin(), types.cend(), PARTITION_DATA) != types.cend();
  if (!m_is_datel && !has_data_partition)
    AddProblem(Severity::High, Common::GetStringT("The data partition is missing."));

  const bool has_channel_partition =
      std::find(types.cbegin(), types.cend(), PARTITION_CHANNEL) != types.cend();
  if (ShouldHaveChannelPartition() && !has_channel_partition)
    AddProblem(Severity::Medium, Common::GetStringT("The channel partition is missing."));

  const bool has_install_partition =
      std::find(types.cbegin(), types.cend(), PARTITION_INSTALL) != types.cend();
  if (ShouldHaveInstallPartition() && !has_install_partition)
    AddProblem(Severity::High, Common::GetStringT("The install partition is missing."));

  if (ShouldHaveMasterpiecePartitions() &&
      types.cend() ==
          std::find_if(types.cbegin(), types.cend(), [](u32 type) { return type >= 0xFF; }))
  {
    // i18n: This string is referring to a game mode in Super Smash Bros. Brawl called Masterpieces
    // where you play demos of NES/SNES/N64 games. Official translations:
    // 名作トライアル (Japanese), Masterpieces (English), Meisterstücke (German), Chefs-d'œuvre
    // (French), Clásicos (Spanish), Capolavori (Italian), 클래식 게임 체험판 (Korean).
    // If your language is not one of the languages above, consider leaving the string untranslated
    // so that people will recognize it as the name of the game mode.
    AddProblem(Severity::Medium, Common::GetStringT("The Masterpiece partitions are missing."));
  }

  for (const Partition& partition : partitions)
  {
    if (m_volume.GetPartitionType(partition) == PARTITION_UPDATE && partition.offset != 0x50000)
    {
      AddProblem(Severity::Low,
                 Common::GetStringT("The update partition is not at its normal position."));
    }

    const u64 normal_data_offset = m_volume.IsEncryptedAndHashed() ? 0xF800000 : 0x838000;
    if (m_volume.GetPartitionType(partition) == PARTITION_DATA &&
        partition.offset != normal_data_offset && !has_channel_partition && !has_install_partition)
    {
      AddProblem(Severity::Low,
                 Common::GetStringT(
                     "The data partition is not at its normal position. This will affect the "
                     "emulated loading times. When using NetPlay or sending input recordings to "
                     "other people, you will experience desyncs if anyone is using a good dump."));
    }
  }

  std::vector<Partition> valid_partitions;
  for (const Partition& partition : partitions)
  {
    if (CheckPartition(partition))
      valid_partitions.push_back(partition);
  }

  return valid_partitions;
}

bool VolumeVerifier::CheckPartition(const Partition& partition)
{
  std::optional<u32> type = m_volume.GetPartitionType(partition);
  if (!type)
  {
    // Not sure if this can happen in practice
    AddProblem(Severity::Medium, Common::GetStringT("The type of a partition could not be read."));
    return false;
  }

  Severity severity = Severity::Medium;
  if (*type == PARTITION_DATA || *type == PARTITION_INSTALL)
    severity = Severity::High;
  else if (*type == PARTITION_UPDATE)
    severity = Severity::Low;

  const std::string name = GetPartitionName(type);

  if (partition.offset % VolumeWii::BLOCK_TOTAL_SIZE != 0 ||
      m_volume.PartitionOffsetToRawOffset(0, partition) % VolumeWii::BLOCK_TOTAL_SIZE != 0)
  {
    AddProblem(
        Severity::Medium,
        StringFromFormat(Common::GetStringT("The %s partition is not properly aligned.").c_str(),
                         name.c_str()));
  }

  if (!m_is_datel)
  {
    CheckCorrectlySigned(
        partition,
        StringFromFormat(Common::GetStringT("The %s partition is not correctly signed.").c_str(),
                         name.c_str()));
  }

  if (m_volume.SupportsIntegrityCheck() && !m_volume.CheckH3TableIntegrity(partition))
  {
    std::string text = StringFromFormat(
        Common::GetStringT("The H3 hash table for the %s partition is not correct.").c_str(),
        name.c_str());
    AddProblem(Severity::Low, std::move(text));
  }

  bool invalid_disc_header = false;
  std::vector<u8> disc_header(0x80);
  constexpr u32 WII_MAGIC = 0x5D1C9EA3;
  if (!m_volume.Read(0, disc_header.size(), disc_header.data(), partition))
  {
    invalid_disc_header = true;
  }
  else if (Common::swap32(disc_header.data() + 0x18) != WII_MAGIC)
  {
    for (size_t i = 0; i < disc_header.size(); i += 4)
    {
      if (Common::swap32(disc_header.data() + i) != i)
      {
        invalid_disc_header = true;
        break;
      }
    }

    // The loop above ends without setting invalid_disc_header for discs that legitimately lack
    // updates. No such discs have been released to end users. Most such discs are debug signed,
    // but there is apparently at least one that is retail signed, the Movie-Ch Install Disc.
    if (!invalid_disc_header)
      return false;
  }
  if (invalid_disc_header)
  {
    // This can happen when certain programs that create WBFS files scrub the entirety of
    // the Masterpiece partitions in Super Smash Bros. Brawl without removing them from
    // the partition table. https://bugs.dolphin-emu.org/issues/8733
    std::string text = StringFromFormat(
        Common::GetStringT("The %s partition does not seem to contain valid data.").c_str(),
        name.c_str());
    AddProblem(severity, std::move(text));
    return false;
  }

  // Prepare for hash verification in the Process step
  if (m_volume.SupportsIntegrityCheck())
  {
    u64 offset = m_volume.PartitionOffsetToRawOffset(0, partition);
    const std::optional<u64> size =
        m_volume.ReadSwappedAndShifted(partition.offset + 0x2bc, PARTITION_NONE);
    const u64 end_offset = offset + size.value_or(0);

    for (size_t i = 0; offset < end_offset; ++i, offset += VolumeWii::BLOCK_TOTAL_SIZE)
      m_blocks.emplace_back(BlockToVerify{partition, offset, i});

    m_block_errors.emplace(partition, 0);
  }

  const DiscIO::FileSystem* filesystem = m_volume.GetFileSystem(partition);
  if (!filesystem)
  {
    if (m_is_datel)
    {
      // Datel's Wii Freeloader has an invalid FST in its only partition
      return true;
    }

    std::string text = StringFromFormat(
        Common::GetStringT("The %s partition does not have a valid file system.").c_str(),
        name.c_str());
    AddProblem(severity, std::move(text));
    return false;
  }

  if (type == PARTITION_UPDATE)
  {
    std::unique_ptr<FileInfo> file_info = filesystem->FindFileInfo("_sys");
    bool has_correct_ios = false;
    if (file_info)
    {
      const IOS::ES::TMDReader& tmd = m_volume.GetTMD(m_volume.GetGamePartition());
      if (tmd.IsValid())
      {
        const std::string correct_ios = "IOS" + std::to_string(tmd.GetIOSId() & 0xFF) + "-";
        for (const FileInfo& f : *file_info)
        {
          if (StringBeginsWith(f.GetName(), correct_ios))
          {
            has_correct_ios = true;
            break;
          }
        }
      }
    }

    if (!has_correct_ios)
    {
      // This is reached for hacked dumps where the update partition has been replaced with
      // a very old update partition so that no updates will be installed.
      AddProblem(
          Severity::Low,
          Common::GetStringT("The update partition does not contain the IOS used by this title."));
    }
  }

  return true;
}

std::string VolumeVerifier::GetPartitionName(std::optional<u32> type) const
{
  if (!type)
    return "???";

  std::string name = NameForPartitionType(*type, false);
  if (ShouldHaveMasterpiecePartitions() && *type > 0xFF)
  {
    // i18n: This string is referring to a game mode in Super Smash Bros. Brawl called Masterpieces
    // where you play demos of NES/SNES/N64 games. This string is referring to a specific such demo
    // rather than the game mode as a whole, so please use the singular form. Official translations:
    // 名作トライアル (Japanese), Masterpieces (English), Meisterstücke (German), Chefs-d'œuvre
    // (French), Clásicos (Spanish), Capolavori (Italian), 클래식 게임 체험판 (Korean).
    // If your language is not one of the languages above, consider leaving the string untranslated
    // so that people will recognize it as the name of the game mode.
    name = StringFromFormat(Common::GetStringT("%s (Masterpiece)").c_str(), name.c_str());
  }
  return name;
}

void VolumeVerifier::CheckCorrectlySigned(const Partition& partition, std::string error_text)
{
  IOS::HLE::Kernel ios;
  const auto es = ios.GetES();
  const std::vector<u8> cert_chain = m_volume.GetCertificateChain(partition);

  if (IOS::HLE::IPC_SUCCESS !=
          es->VerifyContainer(IOS::HLE::Device::ES::VerifyContainerType::Ticket,
                              IOS::HLE::Device::ES::VerifyMode::DoNotUpdateCertStore,
                              m_volume.GetTicket(partition), cert_chain) ||
      IOS::HLE::IPC_SUCCESS !=
          es->VerifyContainer(IOS::HLE::Device::ES::VerifyContainerType::TMD,
                              IOS::HLE::Device::ES::VerifyMode::DoNotUpdateCertStore,
                              m_volume.GetTMD(partition), cert_chain))
  {
    AddProblem(Severity::Low, std::move(error_text));
  }
}

bool VolumeVerifier::IsDebugSigned() const
{
  const IOS::ES::TicketReader& ticket = m_volume.GetTicket(m_volume.GetGamePartition());
  return ticket.IsValid() ? ticket.GetConsoleType() == IOS::HLE::IOSC::ConsoleType::RVT : false;
}

bool VolumeVerifier::ShouldHaveChannelPartition() const
{
  static constexpr std::array<std::string_view, 18> channel_discs = {
      "RFNE01", "RFNJ01", "RFNK01", "RFNP01", "RFNW01", "RFPE01", "RFPJ01", "RFPK01", "RFPP01",
      "RFPW01", "RGWE41", "RGWJ41", "RGWP41", "RGWX41", "RMCE01", "RMCJ01", "RMCK01", "RMCP01",
  };
  assert(std::is_sorted(channel_discs.cbegin(), channel_discs.cend()));

  return std::binary_search(channel_discs.cbegin(), channel_discs.cend(),
                            std::string_view(m_volume.GetGameID()));
}

bool VolumeVerifier::ShouldHaveInstallPartition() const
{
  static constexpr std::array<std::string_view, 4> dragon_quest_x = {"S4MJGD", "S4SJGD", "S6TJGD",
                                                                     "SDQJGD"};
  const std::string& game_id = m_volume.GetGameID();
  return std::any_of(dragon_quest_x.cbegin(), dragon_quest_x.cend(),
                     [&game_id](std::string_view x) { return x == game_id; });
}

bool VolumeVerifier::ShouldHaveMasterpiecePartitions() const
{
  static constexpr std::array<std::string_view, 4> ssbb = {"RSBE01", "RSBJ01", "RSBK01", "RSBP01"};
  const std::string& game_id = m_volume.GetGameID();
  return std::any_of(ssbb.cbegin(), ssbb.cend(),
                     [&game_id](std::string_view x) { return x == game_id; });
}

bool VolumeVerifier::ShouldBeDualLayer() const
{
  // The Japanese versions of Xenoblade and The Last Story are single-layer
  // (unlike the other versions) and must not be added to this list.
  static constexpr std::array<std::string_view, 33> dual_layer_discs = {
      "R3ME01", "R3MP01", "R3OE01", "R3OJ01", "R3OP01", "RSBE01", "RSBJ01", "RSBK01", "RSBP01",
      "RXMJ8P", "S59E01", "S59JC8", "S59P01", "S5QJC8", "SAKENS", "SAKPNS", "SK8V52", "SK8X52",
      "SLSEXJ", "SLSP01", "SQIE4Q", "SQIP4Q", "SQIY4Q", "SR5E41", "SR5P41", "SUOE41", "SUOP41",
      "SVXX52", "SVXY52", "SX4E01", "SX4P01", "SZ3EGT", "SZ3PGT",
  };
  assert(std::is_sorted(dual_layer_discs.cbegin(), dual_layer_discs.cend()));

  return std::binary_search(dual_layer_discs.cbegin(), dual_layer_discs.cend(),
                            std::string_view(m_volume.GetGameID()));
}

void VolumeVerifier::CheckDiscSize(const std::vector<Partition>& partitions)
{
  if (!IsDisc(m_volume.GetVolumeType()))
    return;

  m_biggest_referenced_offset = GetBiggestReferencedOffset(partitions);
  if (ShouldBeDualLayer() && m_biggest_referenced_offset <= SL_DVD_R_SIZE)
  {
    AddProblem(Severity::Medium,
               Common::GetStringT(
                   "This game has been hacked to fit on a single-layer DVD. Some content such as "
                   "pre-rendered videos, extra languages or entire game modes will be broken. "
                   "This problem generally only exists in illegal copies of games."));
  }

  if (!m_volume.IsSizeAccurate())
  {
    AddProblem(Severity::Low,
               Common::GetStringT("The format that the disc image is saved in does not "
                                  "store the size of the disc image."));
  }
  else if (!m_is_tgc)
  {
    const Platform platform = m_volume.GetVolumeType();
    const bool is_gc_size = platform == Platform::GameCubeDisc || m_is_datel;
    const u64 size = m_volume.GetSize();

    const bool valid_gamecube = size == MINI_DVD_SIZE;
    const bool valid_retail_wii = size == SL_DVD_SIZE || size == DL_DVD_SIZE;
    const bool valid_debug_wii = size == SL_DVD_R_SIZE || size == DL_DVD_R_SIZE;

    const bool debug = IsDebugSigned();
    if ((is_gc_size && !valid_gamecube) ||
        (!is_gc_size && (debug ? !valid_debug_wii : !valid_retail_wii)))
    {
      if (debug && valid_retail_wii)
      {
        AddProblem(
            Severity::Low,
            Common::GetStringT("This debug disc image has the size of a retail disc image."));
      }
      else
      {
        if ((is_gc_size && size < MINI_DVD_SIZE) || (!is_gc_size && size < SL_DVD_SIZE))
        {
          AddProblem(
              Severity::Low,
              Common::GetStringT("This disc image has an unusual size. This will likely make the "
                                 "emulated loading times longer. When using NetPlay or sending "
                                 "input recordings to other people, you will likely experience "
                                 "desyncs if anyone is using a good dump."));
        }
        else
        {
          AddProblem(Severity::Low, Common::GetStringT("This disc image has an unusual size."));
        }
      }
    }
  }
}

u64 VolumeVerifier::GetBiggestReferencedOffset(const std::vector<Partition>& partitions) const
{
  const u64 disc_header_size = m_volume.GetVolumeType() == Platform::GameCubeDisc ? 0x460 : 0x50000;
  u64 biggest_offset = disc_header_size;
  for (const Partition& partition : partitions)
  {
    if (partition != PARTITION_NONE)
    {
      const u64 offset = m_volume.PartitionOffsetToRawOffset(0x440, partition);
      biggest_offset = std::max(biggest_offset, offset);
    }

    const std::optional<u64> dol_offset = GetBootDOLOffset(m_volume, partition);
    if (dol_offset)
    {
      const std::optional<u64> dol_size = GetBootDOLSize(m_volume, partition, *dol_offset);
      if (dol_size)
      {
        const u64 offset = m_volume.PartitionOffsetToRawOffset(*dol_offset + *dol_size, partition);
        biggest_offset = std::max(biggest_offset, offset);
      }
    }

    const std::optional<u64> fst_offset = GetFSTOffset(m_volume, partition);
    const std::optional<u64> fst_size = GetFSTSize(m_volume, partition);
    if (fst_offset && fst_size)
    {
      const u64 offset = m_volume.PartitionOffsetToRawOffset(*fst_offset + *fst_size, partition);
      biggest_offset = std::max(biggest_offset, offset);
    }

    const FileSystem* fs = m_volume.GetFileSystem(partition);
    if (fs)
    {
      const u64 offset =
          m_volume.PartitionOffsetToRawOffset(GetBiggestReferencedOffset(fs->GetRoot()), partition);
      biggest_offset = std::max(biggest_offset, offset);
    }
  }
  return biggest_offset;
}

u64 VolumeVerifier::GetBiggestReferencedOffset(const FileInfo& file_info) const
{
  if (file_info.IsDirectory())
  {
    u64 biggest_offset = 0;
    for (const FileInfo& f : file_info)
      biggest_offset = std::max(biggest_offset, GetBiggestReferencedOffset(f));
    return biggest_offset;
  }
  else
  {
    return file_info.GetOffset() + file_info.GetSize();
  }
}

void VolumeVerifier::CheckMisc()
{
  const std::string game_id_unencrypted = m_volume.GetGameID(PARTITION_NONE);
  const std::string game_id_encrypted = m_volume.GetGameID(m_volume.GetGamePartition());

  if (game_id_unencrypted != game_id_encrypted)
  {
    bool inconsistent_game_id = true;
    if (game_id_encrypted == "RELSAB")
    {
      if (StringBeginsWith(game_id_unencrypted, "410"))
      {
        // This is the Wii Backup Disc (aka "pinkfish" disc),
        // which legitimately has an inconsistent game ID.
        inconsistent_game_id = false;
      }
      else if (StringBeginsWith(game_id_unencrypted, "010"))
      {
        // Hacked version of the Wii Backup Disc (aka "pinkfish" disc).
        std::string proper_game_id = game_id_unencrypted;
        proper_game_id[0] = '4';
        AddProblem(
            Severity::Low,
            StringFromFormat(Common::GetStringT("The game ID is %s but should be %s.").c_str(),
                             game_id_unencrypted.c_str(), proper_game_id.c_str()));
        inconsistent_game_id = false;
      }
    }

    if (inconsistent_game_id)
    {
      AddProblem(Severity::Low, Common::GetStringT("The game ID is inconsistent."));
    }
  }

  const Region region = m_volume.GetRegion();

  constexpr std::string_view GAMECUBE_PLACEHOLDER_ID = "RELSAB";
  constexpr std::string_view WII_PLACEHOLDER_ID = "RABAZZ";

  if (game_id_encrypted.size() < 4)
  {
    AddProblem(Severity::Low, Common::GetStringT("The game ID is unusually short."));
  }
  else if (!m_is_datel && game_id_encrypted != GAMECUBE_PLACEHOLDER_ID &&
           game_id_encrypted != WII_PLACEHOLDER_ID)
  {
    char country_code;
    if (IsDisc(m_volume.GetVolumeType()))
      country_code = game_id_encrypted[3];
    else
      country_code = static_cast<char>(m_volume.GetTitleID().value_or(0) & 0xff);

    const Platform platform = m_volume.GetVolumeType();
    const std::optional<u16> revision = m_volume.GetRevision();

    if (CountryCodeToRegion(country_code, platform, region, revision) != region)
    {
      AddProblem(Severity::Medium,
                 Common::GetStringT(
                     "The region code does not match the game ID. If this is because the "
                     "region code has been modified, the game might run at the wrong speed, "
                     "graphical elements might be offset, or the game might not run at all."));
    }
  }

  const IOS::ES::TMDReader& tmd = m_volume.GetTMD(m_volume.GetGamePartition());
  if (tmd.IsValid())
  {
    const u64 ios_id = tmd.GetIOSId() & 0xFF;

    // List of launch day Korean IOSes obtained from https://hackmii.com/2008/09/korean-wii/.
    // More IOSes were released later that were used in Korean games, but they're all over 40.
    // Also, old IOSes like IOS36 did eventually get released for Korean Wiis as part of system
    // updates, but there are likely no Korean games using them since those IOSes were old by then.
    if (region == Region::NTSC_K && ios_id < 40 && ios_id != 4 && ios_id != 9 && ios_id != 21 &&
        ios_id != 37)
    {
      // This is intended to catch pirated Korean games that have had the IOS slot set to 36
      // as a side effect of having to fakesign after changing the common key slot to 0.
      // (IOS36 was the last IOS to have the Trucha bug.) https://bugs.dolphin-emu.org/issues/10319
      AddProblem(
          Severity::High,
          // i18n: You may want to leave the term "ERROR #002" untranslated,
          // since the emulated software always displays it in English.
          Common::GetStringT("This Korean title is set to use an IOS that typically isn't used on "
                             "Korean consoles. This is likely to lead to ERROR #002."));
    }

    if (ios_id >= 0x80)
    {
      // This is also intended to catch fakesigned pirated Korean games,
      // but this time with the IOS slot set to cIOS instead of IOS36.
      AddProblem(Severity::High, Common::GetStringT("This title is set to use an invalid IOS."));
    }
  }

  m_ticket = m_volume.GetTicket(m_volume.GetGamePartition());
  if (m_ticket.IsValid())
  {
    const u8 specified_common_key_index = m_ticket.GetCommonKeyIndex();

    // Wii discs only use common key 0 (regular) and common key 1 (Korean), not common key 2 (vWii).
    if (m_volume.GetVolumeType() == Platform::WiiDisc && specified_common_key_index > 1)
    {
      AddProblem(Severity::High,
                 // i18n: This is "common" as in "shared", not the opposite of "uncommon"
                 Common::GetStringT("This title is set to use an invalid common key."));
    }

    if (m_volume.GetVolumeType() == Platform::WiiWAD)
    {
      m_ticket = m_volume.GetTicketWithFixedCommonKey();
      const u8 fixed_common_key_index = m_ticket.GetCommonKeyIndex();
      if (specified_common_key_index != fixed_common_key_index)
      {
        // Many fakesigned WADs have the common key index set to a (random?) bogus value.
        // For WADs, Dolphin will detect this and use the correct key, making this low severity.
        std::string text = StringFromFormat(
            // i18n: This is "common" as in "shared", not the opposite of "uncommon"
            Common::GetStringT("The specified common key index is %u but should be %u.").c_str(),
            specified_common_key_index, fixed_common_key_index);
        AddProblem(Severity::Low, std::move(text));
      }
    }
  }

  if (IsDisc(m_volume.GetVolumeType()))
  {
    constexpr u32 NKIT_MAGIC = 0x4E4B4954;  // "NKIT"
    if (m_volume.ReadSwapped<u32>(0x200, PARTITION_NONE) == NKIT_MAGIC)
    {
      AddProblem(
          Severity::Low,
          Common::GetStringT("This disc image is in the NKit format. It is not a good dump in its "
                             "current form, but it might become a good dump if converted back. "
                             "The CRC32 of this file might match the CRC32 of a good dump even "
                             "though the files are not identical."));
    }

    if (StringBeginsWith(game_id_unencrypted, "R8P"))
      CheckSuperPaperMario();
  }
}

void VolumeVerifier::CheckSuperPaperMario()
{
  // When Super Paper Mario (any region/revision) reads setup/aa1_01.dat when starting a new game,
  // it also reads a few extra bytes so that the read length is divisible by 0x20. If these extra
  // bytes are zeroes like in good dumps, the game works correctly, but otherwise it can freeze
  // (depending on the exact values of the extra bytes). https://bugs.dolphin-emu.org/issues/11900

  const DiscIO::Partition partition = m_volume.GetGamePartition();
  const FileSystem* fs = m_volume.GetFileSystem(partition);
  if (!fs)
    return;

  std::unique_ptr<FileInfo> file_info = fs->FindFileInfo("setup/aa1_01.dat");
  if (!file_info)
    return;

  const u64 offset = file_info->GetOffset() + file_info->GetSize();
  const u64 length = Common::AlignUp(offset, 0x20) - offset;
  std::vector<u8> data(length);
  if (!m_volume.Read(offset, length, data.data(), partition))
    return;

  if (std::any_of(data.cbegin(), data.cend(), [](u8 x) { return x != 0; }))
  {
    AddProblem(Severity::High,
               Common::GetStringT("Some padding data that should be zero is not zero. "
                                  "This can make the game freeze at certain points."));
  }
}

void VolumeVerifier::SetUpHashing()
{
  if (m_volume.GetVolumeType() == Platform::WiiWAD)
  {
    m_content_offsets = m_volume.GetContentOffsets();
  }
  else if (m_volume.GetVolumeType() == Platform::WiiDisc)
  {
    // Set up a DiscScrubber for checking whether blocks with errors are unused
    m_scrubber.SetupScrub(&m_volume, VolumeWii::BLOCK_TOTAL_SIZE);
  }

  std::sort(m_blocks.begin(), m_blocks.end(),
            [](const BlockToVerify& b1, const BlockToVerify& b2) { return b1.offset < b2.offset; });

  if (m_hashes_to_calculate.crc32)
    m_crc32_context = crc32(0, nullptr, 0);

  if (m_hashes_to_calculate.md5)
  {
    mbedtls_md5_init(&m_md5_context);
    mbedtls_md5_starts_ret(&m_md5_context);
  }

  if (m_hashes_to_calculate.sha1)
  {
    mbedtls_sha1_init(&m_sha1_context);
    mbedtls_sha1_starts_ret(&m_sha1_context);
  }
}

void VolumeVerifier::WaitForAsyncOperations() const
{
  if (m_crc32_future.valid())
    m_crc32_future.wait();
  if (m_md5_future.valid())
    m_md5_future.wait();
  if (m_sha1_future.valid())
    m_sha1_future.wait();
  if (m_content_future.valid())
    m_content_future.wait();
  if (m_block_future.valid())
    m_block_future.wait();
}

bool VolumeVerifier::ReadChunkAndWaitForAsyncOperations(u64 bytes_to_read)
{
  std::vector<u8> data(bytes_to_read);
  {
    std::lock_guard lk(m_volume_mutex);
    if (!m_volume.Read(m_progress, bytes_to_read, data.data(), PARTITION_NONE))
      return false;
  }

  WaitForAsyncOperations();
  m_data = std::move(data);
  return true;
}

void VolumeVerifier::Process()
{
  ASSERT(m_started);
  ASSERT(!m_done);

  if (m_progress == m_max_progress)
    return;

  IOS::ES::Content content{};
  bool content_read = false;
  bool block_read = false;
  u64 bytes_to_read = BLOCK_SIZE;
  if (m_content_index < m_content_offsets.size() &&
      m_content_offsets[m_content_index] == m_progress)
  {
    m_volume.GetTMD(PARTITION_NONE).GetContent(m_content_index, &content);
    bytes_to_read = Common::AlignUp(content.size, 0x40);
    content_read = true;
  }
  else if (m_content_index < m_content_offsets.size() &&
           m_content_offsets[m_content_index] > m_progress)
  {
    bytes_to_read = std::min(bytes_to_read, m_content_offsets[m_content_index] - m_progress);
  }
  else if (m_block_index < m_blocks.size() && m_blocks[m_block_index].offset == m_progress)
  {
    bytes_to_read = VolumeWii::BLOCK_TOTAL_SIZE;
    block_read = true;
  }
  else if (m_block_index < m_blocks.size() && m_blocks[m_block_index].offset > m_progress)
  {
    bytes_to_read = std::min(bytes_to_read, m_blocks[m_block_index].offset - m_progress);
  }
  bytes_to_read = std::min(bytes_to_read, m_max_progress - m_progress);

  const bool is_data_needed = m_calculating_any_hash || content_read || block_read;
  const bool read_succeeded = is_data_needed && ReadChunkAndWaitForAsyncOperations(bytes_to_read);

  if (!read_succeeded)
  {
    ERROR_LOG(DISCIO, "Read failed at 0x%" PRIx64 " to 0x%" PRIx64, m_progress,
              m_progress + bytes_to_read);

    m_read_errors_occurred = true;
    m_calculating_any_hash = false;
  }

  if (m_calculating_any_hash)
  {
    if (m_hashes_to_calculate.crc32)
    {
      m_crc32_future = std::async(std::launch::async, [this] {
        // It would be nice to use crc32_z here instead of crc32, but it isn't available on Android
        m_crc32_context =
            crc32(m_crc32_context, m_data.data(), static_cast<unsigned int>(m_data.size()));
      });
    }

    if (m_hashes_to_calculate.md5)
    {
      m_md5_future = std::async(std::launch::async, [this] {
        mbedtls_md5_update_ret(&m_md5_context, m_data.data(), m_data.size());
      });
    }

    if (m_hashes_to_calculate.sha1)
    {
      m_sha1_future = std::async(std::launch::async, [this] {
        mbedtls_sha1_update_ret(&m_sha1_context, m_data.data(), m_data.size());
      });
    }
  }

  if (content_read)
  {
    m_content_future = std::async(std::launch::async, [this, read_succeeded, content] {
      if (!read_succeeded || !m_volume.CheckContentIntegrity(content, m_data, m_ticket))
      {
        AddProblem(
            Severity::High,
            StringFromFormat(Common::GetStringT("Content %08x is corrupt.").c_str(), content.id));
      }
    });

    m_content_index++;
  }

  if (m_block_index < m_blocks.size() &&
      m_blocks[m_block_index].offset < m_progress + bytes_to_read)
  {
    m_block_future = std::async(
        std::launch::async,
        [this, read_succeeded, bytes_to_read](size_t block_index, u64 progress) {
          while (block_index < m_blocks.size() &&
                 m_blocks[block_index].offset < progress + bytes_to_read)
          {
            bool success;
            if (m_blocks[block_index].offset == progress)
            {
              success = read_succeeded &&
                        m_volume.CheckBlockIntegrity(m_blocks[block_index].block_index, m_data,
                                                     m_blocks[block_index].partition);
            }
            else
            {
              std::lock_guard lk(m_volume_mutex);
              success = m_volume.CheckBlockIntegrity(m_blocks[block_index].block_index,
                                                     m_blocks[block_index].partition);
            }

            const u64 offset = m_blocks[block_index].offset;
            if (success)
            {
              m_biggest_verified_offset =
                  std::max(m_biggest_verified_offset, offset + VolumeWii::BLOCK_TOTAL_SIZE);
            }
            else
            {
              if (m_scrubber.CanBlockBeScrubbed(offset))
              {
                WARN_LOG(DISCIO, "Integrity check failed for unused block at 0x%" PRIx64, offset);
                m_unused_block_errors[m_blocks[block_index].partition]++;
              }
              else
              {
                WARN_LOG(DISCIO, "Integrity check failed for block at 0x%" PRIx64, offset);
                m_block_errors[m_blocks[block_index].partition]++;
              }
            }
            block_index++;
          }
        },
        m_block_index, m_progress);

    while (m_block_index < m_blocks.size() &&
           m_blocks[m_block_index].offset < m_progress + bytes_to_read)
    {
      m_block_index++;
    }
  }

  m_progress += bytes_to_read;
}

u64 VolumeVerifier::GetBytesProcessed() const
{
  return m_progress;
}

u64 VolumeVerifier::GetTotalBytes() const
{
  return m_max_progress;
}

void VolumeVerifier::Finish()
{
  if (m_done)
    return;
  m_done = true;

  WaitForAsyncOperations();

  if (m_calculating_any_hash)
  {
    if (m_hashes_to_calculate.crc32)
    {
      m_result.hashes.crc32 = std::vector<u8>(4);
      const u32 crc32_be = Common::swap32(m_crc32_context);
      const u8* crc32_be_ptr = reinterpret_cast<const u8*>(&crc32_be);
      std::copy(crc32_be_ptr, crc32_be_ptr + 4, m_result.hashes.crc32.begin());
    }

    if (m_hashes_to_calculate.md5)
    {
      m_result.hashes.md5 = std::vector<u8>(16);
      mbedtls_md5_finish_ret(&m_md5_context, m_result.hashes.md5.data());
    }

    if (m_hashes_to_calculate.sha1)
    {
      m_result.hashes.sha1 = std::vector<u8>(20);
      mbedtls_sha1_finish_ret(&m_sha1_context, m_result.hashes.sha1.data());
    }
  }

  if (m_read_errors_occurred)
    AddProblem(Severity::Medium, Common::GetStringT("Some of the data could not be read."));

  bool file_too_small = false;

  if (m_content_index != m_content_offsets.size() || m_block_index != m_blocks.size())
    file_too_small = true;

  if (IsDisc(m_volume.GetVolumeType()) &&
      (m_volume.IsSizeAccurate() || m_volume.SupportsIntegrityCheck()))
  {
    u64 volume_size = m_volume.IsSizeAccurate() ? m_volume.GetSize() : m_biggest_verified_offset;
    if (m_biggest_referenced_offset > volume_size)
      file_too_small = true;
  }

  if (file_too_small)
  {
    const bool second_layer_missing =
        m_biggest_referenced_offset > SL_DVD_SIZE && m_volume.GetSize() >= SL_DVD_SIZE;
    std::string text =
        second_layer_missing ?
            Common::GetStringT("This disc image is too small and lacks some data. The problem is "
                               "most likely that this is a dual-layer disc that has been dumped "
                               "as a single-layer disc.") :
            Common::GetStringT("This disc image is too small and lacks some data. If your "
                               "dumping program saved the disc image as several parts, you need "
                               "to merge them into one file.");
    AddProblem(Severity::High, std::move(text));
  }

  for (auto [partition, blocks] : m_block_errors)
  {
    if (blocks > 0)
    {
      const std::string name = GetPartitionName(m_volume.GetPartitionType(partition));
      std::string text = StringFromFormat(
          Common::GetStringT("Errors were found in %zu blocks in the %s partition.").c_str(),
          blocks, name.c_str());
      AddProblem(Severity::Medium, std::move(text));
    }
  }

  for (auto [partition, blocks] : m_unused_block_errors)
  {
    if (blocks > 0)
    {
      const std::string name = GetPartitionName(m_volume.GetPartitionType(partition));
      std::string text = StringFromFormat(
          Common::GetStringT("Errors were found in %zu unused blocks in the %s partition.").c_str(),
          blocks, name.c_str());
      AddProblem(Severity::Low, std::move(text));
    }
  }

  // Show the most serious problems at the top
  std::stable_sort(m_result.problems.begin(), m_result.problems.end(),
                   [](const Problem& p1, const Problem& p2) { return p1.severity > p2.severity; });
  const Severity highest_severity =
      m_result.problems.empty() ? Severity::None : m_result.problems[0].severity;

  if (m_redump_verification)
    m_result.redump = m_redump_verifier.Finish(m_result.hashes);

  if (m_result.redump.status == RedumpVerifier::Status::GoodDump ||
      (m_volume.GetVolumeType() == Platform::WiiWAD && !m_is_not_retail &&
       m_result.problems.empty()))
  {
    if (m_result.problems.empty())
    {
      m_result.summary_text = Common::GetStringT("This is a good dump.");
    }
    else
    {
      m_result.summary_text =
          Common::GetStringT("This is a good dump according to Redump.org, but Dolphin has found "
                             "problems. This might be a bug in Dolphin.");
    }
    return;
  }

  if (m_is_datel)
  {
    m_result.summary_text = Common::GetStringT("Dolphin is unable to verify unlicensed discs.");
    return;
  }

  if (m_is_tgc)
  {
    m_result.summary_text =
        Common::GetStringT("Dolphin is unable to verify typical TGC files properly, "
                           "since they are not dumps of actual discs.");
    return;
  }

  if (m_result.redump.status == RedumpVerifier::Status::BadDump &&
      highest_severity <= Severity::Low)
  {
    m_result.summary_text = Common::GetStringT(
        "This is a bad dump. This doesn't necessarily mean that the game won't run correctly.");
  }
  else
  {
    if (m_result.redump.status == RedumpVerifier::Status::BadDump)
    {
      m_result.summary_text = Common::GetStringT("This is a bad dump.") + "\n\n";
    }

    switch (highest_severity)
    {
    case Severity::None:
      if (IsWii(m_volume.GetVolumeType()) && !m_is_not_retail)
      {
        m_result.summary_text = Common::GetStringT(
            "No problems were found. This does not guarantee that this is a good dump, "
            "but since Wii titles contain a lot of verification data, it does mean that "
            "there most likely are no problems that will affect emulation.");
      }
      else
      {
        m_result.summary_text = Common::GetStringT("No problems were found.");
      }
      break;
    case Severity::Low:
      m_result.summary_text =
          Common::GetStringT("Problems with low severity were found. They will most "
                             "likely not prevent the game from running.");
      break;
    case Severity::Medium:
      m_result.summary_text +=
          Common::GetStringT("Problems with medium severity were found. The whole game "
                             "or certain parts of the game might not work correctly.");
      break;
    case Severity::High:
      m_result.summary_text += Common::GetStringT(
          "Problems with high severity were found. The game will most likely not work at all.");
      break;
    }
  }

  if (m_volume.GetVolumeType() == Platform::GameCubeDisc)
  {
    m_result.summary_text +=
        Common::GetStringT("\n\nBecause GameCube disc images contain little verification data, "
                           "there may be problems that Dolphin is unable to detect.");
  }
  else if (m_is_not_retail)
  {
    m_result.summary_text +=
        Common::GetStringT("\n\nBecause this title is not for retail Wii consoles, "
                           "Dolphin cannot verify that it hasn't been tampered with.");
  }
}

const VolumeVerifier::Result& VolumeVerifier::GetResult() const
{
  return m_result;
}

void VolumeVerifier::AddProblem(Severity severity, std::string text)
{
  m_result.problems.emplace_back(Problem{severity, std::move(text)});
}

}  // namespace DiscIO
