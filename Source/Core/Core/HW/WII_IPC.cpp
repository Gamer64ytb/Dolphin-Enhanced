// Copyright 2008 Dolphin Emulator Project
// Licensed under GPLv2+
// Refer to the license.txt file included.

#include "Core/HW/WII_IPC.h"

#include "Common/ChunkFile.h"
#include "Common/CommonTypes.h"
#include "Common/Logging/Log.h"
#include "Core/CoreTiming.h"
#include "Core/HW/DVD/DVDInterface.h"
#include "Core/HW/MMIO.h"
#include "Core/HW/ProcessorInterface.h"
#include "Core/IOS/IOS.h"

// This is the intercommunication between ARM and PPC. Currently only PPC actually uses it, because
// of the IOS HLE
// How IOS uses IPC:
// X1 Execute command: a new pointer is available in HW_IPC_PPCCTRL
// X2 Reload (a new IOS is being loaded, old one doesn't need to reply anymore)
// Y1 Command executed and reply available in HW_IPC_ARMMSG
// Y2 Command acknowledge
// ppc_msg is a pointer to 0x40byte command structure
// arm_msg is, similarly, starlet's response buffer*

namespace IOS
{
enum
{
  IPC_PPCMSG = 0x00,
  IPC_PPCCTRL = 0x04,
  IPC_ARMMSG = 0x08,
  IPC_ARMCTRL = 0x0c,

  PPCSPEED = 0x18,
  VISOLID = 0x24,

  PPC_IRQFLAG = 0x30,
  PPC_IRQMASK = 0x34,
  ARM_IRQFLAG = 0x38,
  ARM_IRQMASK = 0x3c,

  GPIOB_OUT = 0xc0,
  GPIOB_DIR = 0xc4,
  GPIOB_IN = 0xc8,

  UNK_180 = 0x180,
  UNK_1CC = 0x1cc,
  UNK_1D0 = 0x1d0,
};

struct CtrlRegister
{
  u8 X1 : 1;
  u8 X2 : 1;
  u8 Y1 : 1;
  u8 Y2 : 1;
  u8 IX1 : 1;
  u8 IX2 : 1;
  u8 IY1 : 1;
  u8 IY2 : 1;

  CtrlRegister() { X1 = X2 = Y1 = Y2 = IX1 = IX2 = IY1 = IY2 = 0; }
  inline u8 ppc() { return (IY2 << 5) | (IY1 << 4) | (X2 << 3) | (Y1 << 2) | (Y2 << 1) | X1; }
  inline u8 arm() { return (IX2 << 5) | (IX1 << 4) | (Y2 << 3) | (X1 << 2) | (X2 << 1) | Y1; }
  inline void ppc(u32 v)
  {
    X1 = v & 1;
    X2 = (v >> 3) & 1;
    if ((v >> 2) & 1)
      Y1 = 0;
    if ((v >> 1) & 1)
      Y2 = 0;
    IY1 = (v >> 4) & 1;
    IY2 = (v >> 5) & 1;
  }

  inline void arm(u32 v)
  {
    Y1 = v & 1;
    Y2 = (v >> 3) & 1;
    if ((v >> 2) & 1)
      X1 = 0;
    if ((v >> 1) & 1)
      X2 = 0;
    IX1 = (v >> 4) & 1;
    IX2 = (v >> 5) & 1;
  }
};

// STATE_TO_SAVE
static u32 ppc_msg;
static u32 arm_msg;
static CtrlRegister ctrl;

static u32 ppc_irq_flags;
static u32 ppc_irq_masks;
static u32 arm_irq_flags;
static u32 arm_irq_masks;

// Indicates which pins are accessible by broadway.  Writable by starlet only.
static constexpr Common::Flags<GPIO> gpio_owner = {GPIO::SLOT_LED, GPIO::SLOT_IN, GPIO::SENSOR_BAR,
                                                   GPIO::DO_EJECT, GPIO::AVE_SCL, GPIO::AVE_SDA};
static Common::Flags<GPIO> gpio_dir;
Common::Flags<GPIO> g_gpio_out;

static CoreTiming::EventType* updateInterrupts;
static void UpdateInterrupts(u64 = 0, s64 cyclesLate = 0);

void DoState(PointerWrap& p)
{
  p.Do(ppc_msg);
  p.Do(arm_msg);
  p.DoPOD(ctrl);
  p.Do(ppc_irq_flags);
  p.Do(ppc_irq_masks);
  p.Do(arm_irq_flags);
  p.Do(arm_irq_masks);
  p.Do(g_gpio_out);
}

static void InitState()
{
  ctrl = CtrlRegister();
  ppc_msg = 0;
  arm_msg = 0;

  ppc_irq_flags = 0;
  ppc_irq_masks = 0;
  arm_irq_flags = 0;
  arm_irq_masks = 0;

  // The only input broadway has is SLOT_IN; all the others it has access to are outputs
  gpio_dir = {GPIO::SLOT_LED, GPIO::SENSOR_BAR, GPIO::DO_EJECT, GPIO::AVE_SCL, GPIO::AVE_SDA};
  g_gpio_out = {};

  ppc_irq_masks |= INT_CAUSE_IPC_BROADWAY;
}

void Init()
{
  InitState();
  updateInterrupts = CoreTiming::RegisterEvent("IPCInterrupt", UpdateInterrupts);
}

void Reset()
{
  INFO_LOG(WII_IPC, "Resetting ...");
  InitState();
}

void Shutdown()
{
}

void RegisterMMIO(MMIO::Mapping* mmio, u32 base)
{
  mmio->Register(base | IPC_PPCMSG, MMIO::InvalidRead<u32>(), MMIO::DirectWrite<u32>(&ppc_msg));

  mmio->Register(base | IPC_PPCCTRL, MMIO::ComplexRead<u32>([](u32) { return ctrl.ppc(); }),
                 MMIO::ComplexWrite<u32>([](u32, u32 val) {
                   ctrl.ppc(val);
                   // The IPC interrupt is triggered when IY1/IY2 is set and
                   // Y1/Y2 is written to -- even when this results in clearing the bit.
                   if ((val >> 2 & 1 && ctrl.IY1) || (val >> 1 & 1 && ctrl.IY2))
                     ppc_irq_flags |= INT_CAUSE_IPC_BROADWAY;
                   if (ctrl.X1)
                     HLE::GetIOS()->EnqueueIPCRequest(ppc_msg);
                   HLE::GetIOS()->UpdateIPC();
                   CoreTiming::ScheduleEvent(0, updateInterrupts, 0);
                 }));

  mmio->Register(base | IPC_ARMMSG, MMIO::DirectRead<u32>(&arm_msg), MMIO::InvalidWrite<u32>());

  mmio->Register(base | PPC_IRQFLAG, MMIO::InvalidRead<u32>(),
                 MMIO::ComplexWrite<u32>([](u32, u32 val) {
                   ppc_irq_flags &= ~val;
                   HLE::GetIOS()->UpdateIPC();
                   CoreTiming::ScheduleEvent(0, updateInterrupts, 0);
                 }));

  mmio->Register(base | PPC_IRQMASK, MMIO::InvalidRead<u32>(),
                 MMIO::ComplexWrite<u32>([](u32, u32 val) {
                   ppc_irq_masks = val;
                   if (ppc_irq_masks & INT_CAUSE_IPC_BROADWAY)  // wtf?
                     Reset();
                   HLE::GetIOS()->UpdateIPC();
                   CoreTiming::ScheduleEvent(0, updateInterrupts, 0);
                 }));

  mmio->Register(base | GPIOB_OUT, MMIO::DirectRead<u32>(&g_gpio_out.m_hex),
                 MMIO::ComplexWrite<u32>([](u32, u32 val) {
                   g_gpio_out.m_hex = val & gpio_owner.m_hex;
                   if (g_gpio_out[GPIO::DO_EJECT])
                   {
                     INFO_LOG(WII_IPC, "Ejecting disc due to GPIO write");
                     DVDInterface::EjectDisc(DVDInterface::EjectCause::Software);
                   }
                   // SENSOR_BAR is checked by WiimoteEmu::CameraLogic
                   // TODO: AVE, SLOT_LED
                 }));
  mmio->Register(base | GPIOB_DIR, MMIO::DirectRead<u32>(&gpio_dir.m_hex),
                 MMIO::DirectWrite<u32>(&gpio_dir.m_hex));
  mmio->Register(base | GPIOB_IN, MMIO::ComplexRead<u32>([](u32) {
                   Common::Flags<GPIO> gpio_in;
                   gpio_in[GPIO::SLOT_IN] = DVDInterface::IsDiscInside();
                   return gpio_in.m_hex;
                 }),
                 MMIO::Nop<u32>());

  // Register some stubbed/unknown MMIOs required to make Wii games work.
  mmio->Register(base | PPCSPEED, MMIO::InvalidRead<u32>(), MMIO::Nop<u32>());
  mmio->Register(base | VISOLID, MMIO::InvalidRead<u32>(), MMIO::Nop<u32>());
  mmio->Register(base | UNK_180, MMIO::Constant<u32>(0), MMIO::Nop<u32>());
  mmio->Register(base | UNK_1CC, MMIO::Constant<u32>(0), MMIO::Nop<u32>());
  mmio->Register(base | UNK_1D0, MMIO::Constant<u32>(0), MMIO::Nop<u32>());
}

static void UpdateInterrupts(u64 userdata, s64 cyclesLate)
{
  if ((ctrl.Y1 & ctrl.IY1) || (ctrl.Y2 & ctrl.IY2))
  {
    ppc_irq_flags |= INT_CAUSE_IPC_BROADWAY;
  }

  if ((ctrl.X1 & ctrl.IX1) || (ctrl.X2 & ctrl.IX2))
  {
    ppc_irq_flags |= INT_CAUSE_IPC_STARLET;
  }

  // Generate interrupt on PI if any of the devices behind starlet have an interrupt and mask is set
  ProcessorInterface::SetInterrupt(ProcessorInterface::INT_CAUSE_WII_IPC,
                                   !!(ppc_irq_flags & ppc_irq_masks));
}

void ClearX1()
{
  ctrl.X1 = 0;
}

void GenerateAck(u32 _Address)
{
  ctrl.Y2 = 1;
  DEBUG_LOG(WII_IPC, "GenerateAck: %08x | %08x [R:%i A:%i E:%i]", ppc_msg, _Address, ctrl.Y1,
            ctrl.Y2, ctrl.X1);
  // Based on a hardware test, the IPC interrupt takes approximately 100 TB ticks to fire
  // after Y2 is seen in the control register.
  CoreTiming::ScheduleEvent(100 * SystemTimers::TIMER_RATIO, updateInterrupts);
}

void GenerateReply(u32 _Address)
{
  arm_msg = _Address;
  ctrl.Y1 = 1;
  DEBUG_LOG(WII_IPC, "GenerateReply: %08x | %08x [R:%i A:%i E:%i]", ppc_msg, _Address, ctrl.Y1,
            ctrl.Y2, ctrl.X1);
  // Based on a hardware test, the IPC interrupt takes approximately 100 TB ticks to fire
  // after Y1 is seen in the control register.
  CoreTiming::ScheduleEvent(100 * SystemTimers::TIMER_RATIO, updateInterrupts);
}

bool IsReady()
{
  return ((ctrl.Y1 == 0) && (ctrl.Y2 == 0) && ((ppc_irq_flags & INT_CAUSE_IPC_BROADWAY) == 0));
}
}  // namespace IOS
