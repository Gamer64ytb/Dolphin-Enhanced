// Copyright 2011 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Core/FifoPlayer/FifoDataFile.h"

#include <algorithm>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "Common/File.h"
#include "Common/MsgHandler.h"
#include "Core/Config/MainSettings.h"
#include "Core/HW/Memmap.h"

enum
{
  FILE_ID = 0x0d01f1f0,
  VERSION_NUMBER = 5,
  MIN_LOADER_VERSION = 1,
  // This value is only used if the DFF file was created with overridden RAM sizes.
  // If the MIN_LOADER_VERSION ever exceeds this, it's alright to remove it.
  MIN_LOADER_VERSION_FOR_RAM_OVERRIDE = 5,
};

#pragma pack(push, 1)

struct FileHeader
{
  u32 fileId;
  u32 file_version;
  u32 min_loader_version;
  u64 bpMemOffset;
  u32 bpMemSize;
  u64 cpMemOffset;
  u32 cpMemSize;
  u64 xfMemOffset;
  u32 xfMemSize;
  u64 xfRegsOffset;
  u32 xfRegsSize;
  u64 frameListOffset;
  u32 frameCount;
  u32 flags;
  u64 texMemOffset;
  u32 texMemSize;
  // These are for overriden RAM sizes.  Otherwise the FIFO Player
  // will crash and burn with mismatched settings.  See PR #8722.
  u32 mem1_size;
  u32 mem2_size;
  u8 reserved[32];
};
static_assert(sizeof(FileHeader) == 128, "FileHeader should be 128 bytes");

struct FileFrameInfo
{
  u64 fifoDataOffset;
  u32 fifoDataSize;
  u32 fifoStart;
  u32 fifoEnd;
  u64 memoryUpdatesOffset;
  u32 numMemoryUpdates;
  u8 reserved[32];
};
static_assert(sizeof(FileFrameInfo) == 64, "FileFrameInfo should be 64 bytes");

struct FileMemoryUpdate
{
  u32 fifoPosition;
  u32 address;
  u64 dataOffset;
  u32 dataSize;
  u8 type;
  u8 reserved[3];
};
static_assert(sizeof(FileMemoryUpdate) == 24, "FileMemoryUpdate should be 24 bytes");

#pragma pack(pop)

FifoDataFile::FifoDataFile() = default;

FifoDataFile::~FifoDataFile() = default;

bool FifoDataFile::ShouldGenerateFakeVIUpdates() const
{
  return true;
}

bool FifoDataFile::HasBrokenEFBCopies() const
{
  return m_Version < 2;
}

void FifoDataFile::SetIsWii(bool isWii)
{
  SetFlag(FLAG_IS_WII, isWii);
}

bool FifoDataFile::GetIsWii() const
{
  return GetFlag(FLAG_IS_WII);
}

void FifoDataFile::AddFrame(const FifoFrameInfo& frameInfo)
{
  m_Frames.push_back(frameInfo);
}

bool FifoDataFile::Save(const std::string& filename)
{
  File::IOFile file;
  if (!file.Open(filename, "wb"))
    return false;

  // Add space for header
  PadFile(sizeof(FileHeader), file);

  // Add space for frame list
  u64 frameListOffset = file.Tell();
  PadFile(m_Frames.size() * sizeof(FileFrameInfo), file);

  u64 bpMemOffset = file.Tell();
  file.WriteArray(m_BPMem, BP_MEM_SIZE);

  u64 cpMemOffset = file.Tell();
  file.WriteArray(m_CPMem, CP_MEM_SIZE);

  u64 xfMemOffset = file.Tell();
  file.WriteArray(m_XFMem, XF_MEM_SIZE);

  u64 xfRegsOffset = file.Tell();
  file.WriteArray(m_XFRegs, XF_REGS_SIZE);

  u64 texMemOffset = file.Tell();
  file.WriteArray(m_TexMem, TEX_MEM_SIZE);

  // Write header
  FileHeader header;
  header.fileId = FILE_ID;
  header.file_version = VERSION_NUMBER;
  // Maintain backwards compatability so long as the RAM sizes aren't overridden.
  if (Config::Get(Config::MAIN_RAM_OVERRIDE_ENABLE))
    header.min_loader_version = MIN_LOADER_VERSION_FOR_RAM_OVERRIDE;
  else
    header.min_loader_version = MIN_LOADER_VERSION;

  header.bpMemOffset = bpMemOffset;
  header.bpMemSize = BP_MEM_SIZE;

  header.cpMemOffset = cpMemOffset;
  header.cpMemSize = CP_MEM_SIZE;

  header.xfMemOffset = xfMemOffset;
  header.xfMemSize = XF_MEM_SIZE;

  header.xfRegsOffset = xfRegsOffset;
  header.xfRegsSize = XF_REGS_SIZE;

  header.texMemOffset = texMemOffset;
  header.texMemSize = TEX_MEM_SIZE;

  header.frameListOffset = frameListOffset;
  header.frameCount = (u32)m_Frames.size();

  header.flags = m_Flags;

  header.mem1_size = Memory::GetRamSizeReal();
  header.mem2_size = Memory::GetExRamSizeReal();

  file.Seek(0, SEEK_SET);
  file.WriteBytes(&header, sizeof(FileHeader));

  // Write frames list
  for (unsigned int i = 0; i < m_Frames.size(); ++i)
  {
    const FifoFrameInfo& srcFrame = m_Frames[i];

    // Write FIFO data
    file.Seek(0, SEEK_END);
    u64 dataOffset = file.Tell();
    file.WriteBytes(srcFrame.fifoData.data(), srcFrame.fifoData.size());

    u64 memoryUpdatesOffset = WriteMemoryUpdates(srcFrame.memoryUpdates, file);

    FileFrameInfo dstFrame;
    dstFrame.fifoDataSize = static_cast<u32>(srcFrame.fifoData.size());
    dstFrame.fifoDataOffset = dataOffset;
    dstFrame.fifoStart = srcFrame.fifoStart;
    dstFrame.fifoEnd = srcFrame.fifoEnd;
    dstFrame.memoryUpdatesOffset = memoryUpdatesOffset;
    dstFrame.numMemoryUpdates = static_cast<u32>(srcFrame.memoryUpdates.size());

    // Write frame info
    u64 frameOffset = frameListOffset + (i * sizeof(FileFrameInfo));
    file.Seek(frameOffset, SEEK_SET);
    file.WriteBytes(&dstFrame, sizeof(FileFrameInfo));
  }

  if (!file.Close())
    return false;

  return true;
}

std::unique_ptr<FifoDataFile> FifoDataFile::Load(const std::string& filename, bool flagsOnly)
{
  File::IOFile file;
  file.Open(filename, "rb");
  if (!file)
    return nullptr;

  FileHeader header;
  file.ReadBytes(&header, sizeof(header));

  if (header.fileId != FILE_ID || header.min_loader_version > VERSION_NUMBER)
  {
    CriticalAlertT(
        "The DFF's minimum loader version (%d) exceeds the version of this FIFO Player (%d)",
        header.min_loader_version, VERSION_NUMBER);
    file.Close();
    return nullptr;
  }

  // Official support for overridden RAM sizes was added in version 5.
  if (header.file_version < 5)
  {
    // It's safe to assume FIFO Logs before PR #8722 weren't using this
    // obscure feature, so load up these header values with the old defaults.
    header.mem1_size = Memory::MEM1_SIZE_RETAIL;
    header.mem2_size = Memory::MEM2_SIZE_RETAIL;
  }

  auto dataFile = std::make_unique<FifoDataFile>();

  dataFile->m_Flags = header.flags;
  dataFile->m_Version = header.file_version;

  if (flagsOnly)
  {
    // Force settings to match those used when the DFF was created.  This is sort of a hack.
    // It only works because this function gets called twice, and the first time (flagsOnly mode)
    // happens to be before HW::Init().  But the convenience is hard to deny!
    Config::SetCurrent(Config::MAIN_RAM_OVERRIDE_ENABLE, true);
    Config::SetCurrent(Config::MAIN_MEM1_SIZE, header.mem1_size);
    Config::SetCurrent(Config::MAIN_MEM2_SIZE, header.mem2_size);

    file.Close();
    return dataFile;
  }

  // To make up for such a hacky thing, here is a catch-all failsafe in case if the above code
  // stops working or is otherwise removed.  As it is, this should never end up running.
  // It should be noted, however, that Dolphin *will still crash* from the nullptr being returned
  // in a non-flagsOnly context, so if this code becomes necessary, it should be moved above the
  // prior conditional.
  if (header.mem1_size != Memory::GetRamSizeReal() ||
      header.mem2_size != Memory::GetExRamSizeReal())
  {
    CriticalAlertT("Emulated memory size mismatch!\n"
                   "Current  | MEM1 %08X (%3dMB)    MEM2 %08X (%3dMB)\n"
                   "DFF      | MEM1 %08X (%3dMB)    MEM2 %08X (%3dMB)",
                   Memory::GetRamSizeReal(), Memory::GetRamSizeReal() / 0x100000,
                   Memory::GetExRamSizeReal(), Memory::GetExRamSizeReal() / 0x100000,
                   header.mem1_size, header.mem1_size / 0x100000, header.mem2_size,
                   header.mem2_size / 0x100000);
    file.Close();
    return nullptr;
  }

  u32 size = std::min<u32>(BP_MEM_SIZE, header.bpMemSize);
  file.Seek(header.bpMemOffset, SEEK_SET);
  file.ReadArray(dataFile->m_BPMem, size);

  size = std::min<u32>(CP_MEM_SIZE, header.cpMemSize);
  file.Seek(header.cpMemOffset, SEEK_SET);
  file.ReadArray(dataFile->m_CPMem, size);

  size = std::min<u32>(XF_MEM_SIZE, header.xfMemSize);
  file.Seek(header.xfMemOffset, SEEK_SET);
  file.ReadArray(dataFile->m_XFMem, size);

  size = std::min<u32>(XF_REGS_SIZE, header.xfRegsSize);
  file.Seek(header.xfRegsOffset, SEEK_SET);
  file.ReadArray(dataFile->m_XFRegs, size);

  // Texture memory saving was added in version 4.
  std::memset(dataFile->m_TexMem, 0, TEX_MEM_SIZE);
  if (dataFile->m_Version >= 4)
  {
    size = std::min<u32>(TEX_MEM_SIZE, header.texMemSize);
    file.Seek(header.texMemOffset, SEEK_SET);
    file.ReadArray(dataFile->m_TexMem, size);
  }

  // idk what else these could be used for, but it'd be a shame to not make them available.
  dataFile->m_ram_size_real = header.mem1_size;
  dataFile->m_exram_size_real = header.mem2_size;

  // Read frames
  for (u32 i = 0; i < header.frameCount; ++i)
  {
    u64 frameOffset = header.frameListOffset + (i * sizeof(FileFrameInfo));
    file.Seek(frameOffset, SEEK_SET);
    FileFrameInfo srcFrame;
    file.ReadBytes(&srcFrame, sizeof(FileFrameInfo));

    FifoFrameInfo dstFrame;
    dstFrame.fifoData.resize(srcFrame.fifoDataSize);
    dstFrame.fifoStart = srcFrame.fifoStart;
    dstFrame.fifoEnd = srcFrame.fifoEnd;

    file.Seek(srcFrame.fifoDataOffset, SEEK_SET);
    file.ReadBytes(dstFrame.fifoData.data(), srcFrame.fifoDataSize);

    ReadMemoryUpdates(srcFrame.memoryUpdatesOffset, srcFrame.numMemoryUpdates,
                      dstFrame.memoryUpdates, file);

    dataFile->AddFrame(dstFrame);
  }

  file.Close();

  return dataFile;
}

void FifoDataFile::PadFile(size_t numBytes, File::IOFile& file)
{
  for (size_t i = 0; i < numBytes; ++i)
    fputc(0, file.GetHandle());
}

void FifoDataFile::SetFlag(u32 flag, bool set)
{
  if (set)
    m_Flags |= flag;
  else
    m_Flags &= ~flag;
}

bool FifoDataFile::GetFlag(u32 flag) const
{
  return !!(m_Flags & flag);
}

u64 FifoDataFile::WriteMemoryUpdates(const std::vector<MemoryUpdate>& memUpdates,
                                     File::IOFile& file)
{
  // Add space for memory update list
  u64 updateListOffset = file.Tell();
  PadFile(memUpdates.size() * sizeof(FileMemoryUpdate), file);

  for (unsigned int i = 0; i < memUpdates.size(); ++i)
  {
    const MemoryUpdate& srcUpdate = memUpdates[i];

    // Write memory
    file.Seek(0, SEEK_END);
    u64 dataOffset = file.Tell();
    file.WriteBytes(srcUpdate.data.data(), srcUpdate.data.size());

    FileMemoryUpdate dstUpdate;
    dstUpdate.address = srcUpdate.address;
    dstUpdate.dataOffset = dataOffset;
    dstUpdate.dataSize = static_cast<u32>(srcUpdate.data.size());
    dstUpdate.fifoPosition = srcUpdate.fifoPosition;
    dstUpdate.type = srcUpdate.type;

    u64 updateOffset = updateListOffset + (i * sizeof(FileMemoryUpdate));
    file.Seek(updateOffset, SEEK_SET);
    file.WriteBytes(&dstUpdate, sizeof(FileMemoryUpdate));
  }

  return updateListOffset;
}

void FifoDataFile::ReadMemoryUpdates(u64 fileOffset, u32 numUpdates,
                                     std::vector<MemoryUpdate>& memUpdates, File::IOFile& file)
{
  memUpdates.resize(numUpdates);

  for (u32 i = 0; i < numUpdates; ++i)
  {
    u64 updateOffset = fileOffset + (i * sizeof(FileMemoryUpdate));
    file.Seek(updateOffset, SEEK_SET);
    FileMemoryUpdate srcUpdate;
    file.ReadBytes(&srcUpdate, sizeof(FileMemoryUpdate));

    MemoryUpdate& dstUpdate = memUpdates[i];
    dstUpdate.address = srcUpdate.address;
    dstUpdate.fifoPosition = srcUpdate.fifoPosition;
    dstUpdate.data.resize(srcUpdate.dataSize);
    dstUpdate.type = static_cast<MemoryUpdate::Type>(srcUpdate.type);

    file.Seek(srcUpdate.dataOffset, SEEK_SET);
    file.ReadBytes(dstUpdate.data.data(), srcUpdate.dataSize);
  }
}
