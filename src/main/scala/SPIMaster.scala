package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPIMaster(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)
    val pins = new Bundle {
      val miso = Input(Bool())
      val mosi = Output(UInt(1.W))
      val sclk = Output(Bool())
      val cs = Output(Bool())
    }
  })
  val regs = new SPIRegs(p)

  val spiTransmit = RegInit(0.U(p.dataWidth.W))
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))
  val sclkReg = RegInit(false.B)
  val prevClk = RegInit(false.B)

  // Clock generation
  val sclkCounter = RegInit(0.U(log2Ceil(p.clockFreq).W))
  when(sclkCounter === (p.clockFreq / 2 - 1).U) {
    prevClk := sclkReg
    sclkReg := ~sclkReg
    sclkCounter := 0.U
  }.otherwise {
    sclkCounter := sclkCounter + 1.U
  }

  io.pins.sclk := sclkReg
  object State extends ChiselEnum {
    val IDLE, DUPLEX = Value
  }
  import State._

  val stateReg = RegInit(IDLE)
  io.apb.PRDATA := 0.U

  switch(stateReg) {
    is(IDLE) {
      printf("IDLE\n")
      shiftCounter := 0.U
      sclkReg := !((p.spiMode == 1).B || (p.spiMode == 2).B)
      when(io.apb.PSEL && io.apb.PENABLE) {
        when(io.apb.PWRITE) {
          spiTransmit := io.apb.PWDATA
          stateReg := DUPLEX
        }.otherwise {
          io.apb.PRDATA := spiTransmit
          stateReg := IDLE
        }
      }.otherwise {
        stateReg := IDLE
      }
    }
    is(DUPLEX) {
      when((p.spiMode == 1).B || (p.spiMode == 4).B) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth).U) {
            spiTransmit := io.pins.miso ## spiTransmit(p.dataWidth - 1, 1)
            printf("TRANSMIT: %x\n", spiTransmit(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := DUPLEX
          }.otherwise {
            stateReg := IDLE
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            spiTransmit := io.pins.miso ## spiTransmit(p.dataWidth - 1, 1)
            printf("TRANSMIT: %x\n", spiTransmit(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := DUPLEX
          }.otherwise {
            stateReg := IDLE
          }
        }
      }
    }
  }
  io.pins.mosi := spiTransmit(0)
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  io.pins.cs := (stateReg === IDLE)
  when(io.apb.PSEL && io.apb.PENABLE && (stateReg === DUPLEX)) {
    io.apb.PSLVERR := 1.U
  }.otherwise {
    io.apb.PSLVERR := 0.U
  }

  def registerWrite(addr: UInt): Unit = {
    // Probably need all regs except DATA to be locked to 1 Byte
    when(addr >= regs.CTRLA_ADDR.U && addr <= regs.CTRLA_ADDR_MAX.U) {
      printf(
        "Writing CTRLA Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      val shiftAddr = (addr - regs.CTRLA_ADDR.U)
      regs.CTRLA := (io.apb.PWDATA(regs.CTRLA_SIZE - 1, 0) << (shiftAddr(
        regs.CTRLA_REG_SIZE - 1,
        0
      ) * 8.U))
    }
    when(addr >= regs.CTRLB_ADDR.U && addr <= regs.CTRLB_ADDR_MAX.U) {
      printf(
        "Writing CTRLB Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      val shiftAddr = (addr - regs.CTRLB_ADDR.U)
      regs.CTRLB := (io.apb.PWDATA(regs.CTRLB_SIZE - 1, 0) << (shiftAddr(
        regs.CTRLB_REG_SIZE - 1,
        0
      ) * 8.U))
    }
    when(addr >= regs.INTCTRL_ADDR.U && addr <= regs.INTCTRL_ADDR_MAX.U) {
      printf(
        "Writing INTCTRL Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      val shiftAddr = (addr - regs.INTCTRL_ADDR.U)
      regs.INTCTRL := (io.apb.PWDATA(regs.INTCTRL_SIZE - 1, 0) << (shiftAddr(
        regs.INTCTRL_REG_SIZE - 1,
        0
      ) * 8.U))
    }
    when(addr >= regs.INTFLAGS_ADDR.U && addr <= regs.INTFLAGS_ADDR_MAX.U) {
      printf(
        "Writing INTFLAGS Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      val shiftAddr = (addr - regs.INTFLAGS_ADDR.U)
      regs.INTFLAGS := (io.apb.PWDATA(regs.INTFLAGS_SIZE - 1, 0) << (shiftAddr(
        regs.INTFLAGS_REG_SIZE - 1,
        0
      ) * 8.U))
    }
    when(
      addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U
    ) {
      printf(
        "Writing DATA Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      val shiftAddr = (addr - regs.DATA_ADDR.U)
      regs.DATA := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(
        regs.DATA_REG_SIZE - 1,
        0
      ) * 8.U))
    }
  }

  def registerRead(addr: UInt): Unit = {
    when(addr >= regs.CTRLA_ADDR.U && addr <= regs.CTRLA_ADDR_MAX.U) {
      printf(
        "Reading CTRLA Register, data: %x, addr: %x\n",
        regs.CTRLA,
        addr
      )
      io.apb.PRDATA := regs.CTRLA
    }
    when(addr >= regs.CTRLB_ADDR.U && addr <= regs.CTRLB_ADDR_MAX.U) {
      printf("Reading CTRLB Register, data: %x, addr: %x\n", regs.CTRLB, addr)
      io.apb.PRDATA := regs.CTRLB
    }
    when(addr >= regs.INTCTRL_ADDR.U && addr <= regs.INTCTRL_ADDR_MAX.U) {
      printf(
        "Reading INTCTRL Register, data: %x, addr: %x\n",
        regs.INTCTRL,
        addr
      )
      io.apb.PRDATA := regs.INTCTRL
    }
    when(addr >= regs.INTFLAGS_ADDR.U && addr <= regs.INTFLAGS_ADDR_MAX.U) {
      printf(
        "Reading INTFLAGS Register, data: %x, addr: %x\n",
        regs.INTFLAGS,
        addr
      )
      io.apb.PRDATA := regs.INTFLAGS
    }
    when(addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U) {
      printf("Reading DATA Register, data: %x, addr: %x\n", regs.DATA, addr)
      io.apb.PRDATA := regs.DATA
    }
  }

}
