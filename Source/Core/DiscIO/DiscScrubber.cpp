// Copyright 2009 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "DiscIO/DiscScrubber.h"

#include <algorithm>
#include <cinttypes>
#include <cstddef>
#include <cstdio>
#include <memory>
#include <optional>
#include <string>
#include <vector>

#include "Common/Align.h"
#include "Common/Assert.h"
#include "Common/CommonTypes.h"
#include "Common/File.h"
#include "Common/Logging/Log.h"

#include "DiscIO/DiscExtractor.h"
#include "DiscIO/Filesystem.h"
#include "DiscIO/Volume.h"

namespace DiscIO
{
DiscScrubber::DiscScrubber() = default;
DiscScrubber::~DiscScrubber() = default;

bool DiscScrubber::SetupScrub(const Volume* disc)
{
  if (!disc)
    return false;
  m_disc = disc;

  m_file_size = m_disc->GetSize();

  // Round up when diving by CLUSTER_SIZE, otherwise MarkAsUsed might write out of bounds
  const size_t num_clusters = static_cast<size_t>((m_file_size + CLUSTER_SIZE - 1) / CLUSTER_SIZE);

  // Table of free blocks
  m_free_table.resize(num_clusters, 1);

  // Fill out table of free blocks
  const bool success = ParseDisc();

  m_is_scrubbing = success;
  return success;
}

bool DiscScrubber::CanBlockBeScrubbed(u64 offset) const
{
  return m_is_scrubbing && m_free_table[offset / CLUSTER_SIZE];
}

void DiscScrubber::MarkAsUsed(u64 offset, u64 size)
{
  u64 current_offset = Common::AlignDown(offset, CLUSTER_SIZE);
  const u64 end_offset = offset + size;

  DEBUG_LOG(DISCIO, "Marking 0x%016" PRIx64 " - 0x%016" PRIx64 " as used", offset, end_offset);

  while (current_offset < end_offset && current_offset < m_file_size)
  {
    m_free_table[current_offset / CLUSTER_SIZE] = 0;
    current_offset += CLUSTER_SIZE;
  }
}

void DiscScrubber::MarkAsUsedE(u64 partition_data_offset, u64 offset, u64 size)
{
  if (partition_data_offset == 0)
  {
    MarkAsUsed(offset, size);
  }
  else
  {
    u64 first_cluster_start = ToClusterOffset(offset) + partition_data_offset;

    u64 last_cluster_end;
    if (size == 0)
    {
      // Without this special case, a size of 0 can be rounded to 1 cluster instead of 0
      last_cluster_end = first_cluster_start;
    }
    else
    {
      last_cluster_end = ToClusterOffset(offset + size - 1) + CLUSTER_SIZE + partition_data_offset;
    }

    MarkAsUsed(first_cluster_start, last_cluster_end - first_cluster_start);
  }
}

// Compensate for 0x400 (SHA-1) per 0x8000 (cluster), and round to whole clusters
u64 DiscScrubber::ToClusterOffset(u64 offset) const
{
  if (m_disc->IsEncryptedAndHashed())
    return offset / 0x7c00 * CLUSTER_SIZE;
  else
    return Common::AlignDown(offset, CLUSTER_SIZE);
}

// Helper functions for reading the BE volume
bool DiscScrubber::ReadFromVolume(u64 offset, u32& buffer, const Partition& partition)
{
  std::optional<u32> value = m_disc->ReadSwapped<u32>(offset, partition);
  if (value)
    buffer = *value;
  return value.has_value();
}

bool DiscScrubber::ReadFromVolume(u64 offset, u64& buffer, const Partition& partition)
{
  std::optional<u64> value = m_disc->ReadSwappedAndShifted(offset, partition);
  if (value)
    buffer = *value;
  return value.has_value();
}

bool DiscScrubber::ParseDisc()
{
  if (m_disc->GetPartitions().empty())
    return ParsePartitionData(PARTITION_NONE);

  // Mark the header as used - it's mostly 0s anyways
  MarkAsUsed(0, 0x50000);

  for (const DiscIO::Partition& partition : m_disc->GetPartitions())
  {
    u32 tmd_size;
    u64 tmd_offset;
    u32 cert_chain_size;
    u64 cert_chain_offset;
    u64 h3_offset;
    // The H3 size is always 0x18000

    if (!ReadFromVolume(partition.offset + 0x2a4, tmd_size, PARTITION_NONE) ||
        !ReadFromVolume(partition.offset + 0x2a8, tmd_offset, PARTITION_NONE) ||
        !ReadFromVolume(partition.offset + 0x2ac, cert_chain_size, PARTITION_NONE) ||
        !ReadFromVolume(partition.offset + 0x2b0, cert_chain_offset, PARTITION_NONE) ||
        !ReadFromVolume(partition.offset + 0x2b4, h3_offset, PARTITION_NONE))
    {
      return false;
    }

    MarkAsUsed(partition.offset, 0x2c0);

    MarkAsUsed(partition.offset + tmd_offset, tmd_size);
    MarkAsUsed(partition.offset + cert_chain_offset, cert_chain_size);
    MarkAsUsed(partition.offset + h3_offset, 0x18000);

    // Parse Data! This is where the big gain is
    if (!ParsePartitionData(partition))
      return false;
  }

  return true;
}

// Operations dealing with encrypted space are done here
bool DiscScrubber::ParsePartitionData(const Partition& partition)
{
  const FileSystem* filesystem = m_disc->GetFileSystem(partition);
  if (!filesystem)
  {
    ERROR_LOG(DISCIO, "Failed to read file system for the partition at 0x%" PRIx64,
              partition.offset);
    return false;
  }

  u64 partition_data_offset;
  if (partition == PARTITION_NONE)
  {
    partition_data_offset = 0;
  }
  else
  {
    u64 data_offset;
    if (!ReadFromVolume(partition.offset + 0x2b8, data_offset, PARTITION_NONE))
      return false;

    partition_data_offset = partition.offset + data_offset;
  }

  // Mark things as used which are not in the filesystem
  // Header, Header Information, Apploader
  u32 apploader_size;
  u32 apploader_trailer_size;
  if (!ReadFromVolume(0x2440 + 0x14, apploader_size, partition) ||
      !ReadFromVolume(0x2440 + 0x18, apploader_trailer_size, partition))
  {
    return false;
  }
  MarkAsUsedE(partition_data_offset, 0, 0x2440 + apploader_size + apploader_trailer_size);

  // DOL
  const std::optional<u64> dol_offset = GetBootDOLOffset(*m_disc, partition);
  if (!dol_offset)
    return false;
  const std::optional<u64> dol_size = GetBootDOLSize(*m_disc, partition, *dol_offset);
  if (!dol_size)
    return false;
  MarkAsUsedE(partition_data_offset, *dol_offset, *dol_size);

  // FST
  const std::optional<u64> fst_offset = GetFSTOffset(*m_disc, partition);
  const std::optional<u64> fst_size = GetFSTSize(*m_disc, partition);
  if (!fst_offset || !fst_size)
    return false;
  MarkAsUsedE(partition_data_offset, *fst_offset, *fst_size);

  // Go through the filesystem and mark entries as used
  ParseFileSystemData(partition_data_offset, filesystem->GetRoot());

  return true;
}

void DiscScrubber::ParseFileSystemData(u64 partition_data_offset, const FileInfo& directory)
{
  for (const DiscIO::FileInfo& file_info : directory)
  {
    DEBUG_LOG(DISCIO, "Scrubbing %s", file_info.GetPath().c_str());
    if (file_info.IsDirectory())
      ParseFileSystemData(partition_data_offset, file_info);
    else
      MarkAsUsedE(partition_data_offset, file_info.GetOffset(), file_info.GetSize());
  }
}

}  // namespace DiscIO
