// Copyright 2008 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#pragma once

#include <cstdio>
#include <memory>
#include <string>

#include "Common/CommonTypes.h"
#include "Common/File.h"
#include "DiscIO/Blob.h"

namespace DiscIO
{
class PlainFileReader : public BlobReader
{
public:
  static std::unique_ptr<PlainFileReader> Create(File::IOFile file);

  BlobType GetBlobType() const override { return BlobType::PLAIN; }

  u64 GetRawSize() const override { return m_size; }
  u64 GetDataSize() const override { return m_size; }
  bool IsDataSizeAccurate() const override { return true; }

  u64 GetBlockSize() const override { return 0; }
  bool HasFastRandomAccessInBlock() const override { return true; }
  std::string GetCompressionMethod() const override { return {}; }

  bool Read(u64 offset, u64 nbytes, u8* out_ptr) override;

private:
  PlainFileReader(File::IOFile file);

  File::IOFile m_file;
  s64 m_size;
};

}  // namespace DiscIO
