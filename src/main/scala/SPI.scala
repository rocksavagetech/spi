package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPI(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)
    val master = new MasterInterface
    val slave = new SlaveInterface
  })

  // Control Registers
  val regs = new SPIRegs(p)

  // Shift Register
  val spiShift = RegInit(0.U(p.dataWidth.W))
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))

  // Flags
  val writeData = RegInit(false.B)
  val readData = RegInit(false.B)

  // Transmit and Recieve Buffer
  val transmitBuffer = RegInit(VecInit(Seq.fill(8)(0.U(p.dataWidth.W))))
  val transmitIndex = RegInit(0.U(3.W))

  val recieveBuffer = RegInit(VecInit(Seq.fill(8)(0.U(p.dataWidth.W))))
  val recieveIndex = RegInit(0.U(3.W))
  val recieveReg = RegInit(
    0.U(p.dataWidth.W)
  ) // Recieve has one reg buffer even in Normal Mode

  // Master Clock generation
  val sclkReg = RegInit(false.B)
  val prevClk = RegInit(false.B)
  when(regs.CTRLA(5) === 1.U) { // Master mode Enabled
    val sclkCounter = RegInit(0.U(8.W))
    val prescalerMap: Map[UInt, UInt] = Map(
      0.U -> 4.U(8.W),
      1.U -> 16.U(8.W),
      2.U -> 64.U(8.W),
      3.U -> 128.U(8.W)
    )
    val clk2xMap: Map[UInt, UInt] = Map(
      0.U -> 1.U(8.W),
      1.U -> 2.U(8.W)
    )
    when(
      sclkCounter === ((prescalerMap.getOrElse(regs.CTRLA(1, 0), 4.U) / clk2xMap
        .getOrElse(regs.CTRLA(4), 1.U)) - 1.U)
    ) {
      prevClk := sclkReg
      sclkReg := ~sclkReg
      sclkCounter := 0.U
    }.otherwise {
      sclkCounter := sclkCounter + 1.U
    }
    io.master.sclk := sclkReg
  }.otherwise { // Temporary to pass build
    io.master.sclk := 0.U
  }
  io.slave.miso := 0.U

  // State Machine Initialization
  object State extends ChiselEnum {
    val idle, normalMaster, bufferMaster, normalSlave, bufferSlave, normalDone,
        bufferDone = Value
  }
  import State._
  val stateReg = RegInit(idle)
  io.apb.PRDATA := 0.U

  // Control Register Read/Write
  when(io.apb.PSEL && io.apb.PENABLE) {
    when(io.apb.PWRITE) {
      registerWrite(io.apb.PADDR)
    }.otherwise {
      registerRead(io.apb.PADDR)
    }
  }

  // Signal Control
  io.master.mosi := spiShift(0)
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  io.master.cs := (stateReg === idle)
  io.apb.PSLVERR := 0.U

  // Error Handling
  when((writeData) && (stateReg === normalMaster)) {
    regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) // Write Collision
  }

  // Buffer should be able to be written to during a transmission

  switch(stateReg) {
    is(idle) {
      printf("idle\n")
      shiftCounter := 0.U
      when(regs.CTRLA(5) === 1.U) {
        sclkReg := !((regs
          .CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))
        when(
          (writeData) && (regs.CTRLB(6) === 0.U) && (regs.CTRLA(0) === 1.U)
        ) { // In Normal Mode, when the DATA register is written to and SPI is enabled
          writeData := false.B
          stateReg := normalMaster
        }.otherwise {
          stateReg := idle
        }
      }
    }
    is(normalMaster) {
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.master.miso ## spiShift(p.dataWidth - 1, 1)
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 1, 1) ## io.master.miso
            }
            printf("TRANSMIT: %x\n", spiShift(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := normalMaster
          }.otherwise {
            stateReg := normalDone
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.master.miso ## spiShift(p.dataWidth - 1, 1)
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 1, 1) ## io.master.miso
            }
            printf("TRANSMIT: %x\n", spiShift(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := normalMaster
          }.otherwise {
            stateReg := normalDone
          }
        }
      }
    }
    is(normalDone) {
      recieveReg := spiShift
      when (regs.INTCTRL(0) === 1.U) {  //When interrupts are enabled
        regs.INTFLAGS := regs.INTFLAGS | (1.U << 7.U) //Set it
      }
      stateReg := idle
    }
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
      regs.INTFLAGS := regs.INTFLAGS & ~(io.apb.PWDATA(regs.INTFLAGS_SIZE - 1, 0) << (shiftAddr(regs.INTFLAGS_REG_SIZE - 1, 0) * 8.U)) //Writing a 1 will clear the interrupt status
    }
    when(
      addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U
    ) {
      printf(
        "Writing DATA Register, data: %x, addr: %x\n",
        io.apb.PWDATA,
        addr
      )
      writeData := true.B
      val shiftAddr = (addr - regs.DATA_ADDR.U)
      spiShift := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(
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
      printf("Reading DATA Register, data: %x, addr: %x\n", recieveReg, addr)
      io.apb.PRDATA := recieveReg
      readData := true.B
    }
  }

}
