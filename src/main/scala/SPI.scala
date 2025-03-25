package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._
import tech.rocksavage.test.TestUtils.coverAll

/**
  * SPI (Serial Peripheral Interface) module.
  *
  * This module implements an SPI master and slave interface with configurable parameters.
  * It supports both master and slave modes, with control registers for configuration,
  * and handles data transmission and reception.
  *
  * @param p BaseParams object containing configuration parameters for the SPI module.
  */
class SPI(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)    // APB (Advanced Peripheral Bus) interface for register access
    val master = new MasterInterface // SPI master interface
    val slave = new SlaveInterface   // SPI slave interface
  })

  // Control Registers
  /** Control registers for SPI configuration and status. */
  val regs = new SPIRegs(p)

  // Shift Register
  /** Shift register for SPI data transmission and reception. */
  val spiShift = RegInit(0.U(p.dataWidth.W))
  /** Counter to track the number of bits shifted. */
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))

  // Internal Flags
  /** Flag to indicate when data is written to the transmit buffer. */
  val writeData = RegInit(false.B)

  // Transmit and Receive Buffer
  /** Buffer for holding data to be transmitted. */
  val transmitBuffer = RegInit(0.U(p.dataWidth.W))
  /** Buffer for holding received data. */
  val recieveBuffer = RegInit(0.U(p.dataWidth.W))
  /** Register to hold the most recently received data. */
  val recieveReg = RegInit(0.U(p.dataWidth.W)) // Receive has one reg buffer even in Normal Mode

  // State Machine Initialization
  /** Enumeration for SPI state machine states. */
  object State extends ChiselEnum {
    val idle, masterMode, slaveMode, complete = Value
  }
  import State._
  /** State register for the SPI state machine. */
  val stateReg = RegInit(idle)

  // Master Clock Generation & MOSI Control
  /** Register for the SPI master clock signal. */
  val sclkReg = RegInit(false.B)
  /** Register to store the previous clock state for edge detection. */
  val prevClk = RegInit(false.B)
  /** Counter for generating the SPI master clock. */
  val sclkCounter = RegInit(0.U(8.W))
  val transitionCounter = RegInit(0.U(8.W))

  // Master Mode Logic
  when(regs.CTRLA(5) === 1.U) { // Master Mode Enabled.
    when(regs.CTRLA(6) === 0.U) {
      io.master.mosi := spiShift(p.dataWidth - 1)
    }.otherwise {
      io.master.mosi := spiShift(0)
    }
    io.master.cs := ~(stateReg === masterMode)
    io.slave.miso := 0.U // MISO Off in Master Mode

    //when(~io.master.cs) {
      when(sclkCounter === (((2.U << (regs.CTRLA(2, 1) * 2.U)) >> (regs.CTRLA(4))) - 1.U)) {
        sclkReg := ~sclkReg
        transitionCounter := transitionCounter + 1.U
        sclkCounter := 0.U
      }.otherwise {
        sclkCounter := sclkCounter + 1.U
      }
    //}
    io.master.sclk := Mux(transitionCounter <= ((p.dataWidth << 1).U),
    sclkReg, 
    !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))
    )
    }.otherwise {
    io.master.sclk := 0.U // Master clk off in slave mode
    io.master.mosi := 0.U // MOSI off in slave Mode
    io.master.cs := 0.U   // Master CS off in slave mode
    when(regs.CTRLA(6) === 0.U) {
      io.slave.miso := spiShift(p.dataWidth - 1)
    }.otherwise {
      io.slave.miso := spiShift(0)
    }
  }

  // APB Interface Logic
  io.apb.PRDATA := 0.U
  /** Handles APB register writes. */
  when(io.apb.PSEL && io.apb.PENABLE) {
    when(io.apb.PWRITE) {
      registerWrite(io.apb.PADDR)
    }.otherwise {
      registerRead(io.apb.PADDR)
    }
  }

  // APB Signal Control
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  /** Handles invalid APB address cases. */
  when((io.apb.PADDR < regs.CTRLA_ADDR.U) || (io.apb.PADDR > regs.DATA_ADDR_MAX.U)) {
    io.apb.PSLVERR := true.B // Set error signal
  }.otherwise {
    io.apb.PSLVERR := false.B // Clear error signal if valid
  }

  // Error Handling
  /** Handles write collision errors in master mode. */
  when((writeData) && (stateReg === masterMode)) {
    when(regs.CTRLB(7) === 0.U) { // In Normal Mode
      regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) // Write Collision
    }
  }

  // State Machine Logic
  switch(stateReg) {
    is(idle) {
      shiftCounter := 0.U
      transitionCounter := 0.U
      when(regs.CTRLA(5) === 1.U) { // Master Mode
        io.master.sclk := !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))        
        sclkReg := !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))
        when((writeData) && (regs.CTRLA(0) === 1.U)) { // When the DATA register is written to and SPI is enabled
          when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U) { // In Buffer mode, when buffer has data
            spiShift := transmitBuffer
            regs.INTFLAGS := regs.INTFLAGS & ~(1.U << 5.U) // Buffer can be overriden now
          }
          writeData := false.B
          io.master.cs := false.B
          stateReg := masterMode
        }.otherwise {
          stateReg := idle
        }
      }.otherwise { // Slave Mode
        when(~io.slave.cs && (regs.CTRLA(0) === 1.U)) { // When slave is selected and SPI is enabled
          when(regs.CTRLB(7) === 1.U) {
            spiShift := transmitBuffer
            regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Buffer can be overriden now
          }
          writeData := false.B
          stateReg := slaveMode
        }.otherwise {
          stateReg := idle
        }
      }
    }
    is(masterMode) {
      prevClk := sclkReg
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.master.miso ## spiShift(p.dataWidth - 1, 1)
              printf("MASTER TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.master.miso
              printf("MASTER TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := masterMode
          }.otherwise {
            io.master.cs := true.B
            stateReg := complete
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.master.miso ## spiShift(p.dataWidth - 1, 1)
              printf("MASTER TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.master.miso
              printf("MASTER TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := masterMode
          }.otherwise {
            io.master.cs := true.B
            stateReg := complete
          }
        }
      }
    }
    is(slaveMode) {
      prevClk := io.slave.sclk
      when(io.slave.cs){
        stateReg := complete
      }
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { // Sample on Rising Edge
        when(~prevClk & io.slave.sclk) {
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.slave.mosi ## spiShift(p.dataWidth - 1, 1)
              printf("SLAVE TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.slave.mosi
              printf("SLAVE TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := Mux(io.slave.cs, complete, slaveMode)
          }.otherwise {
            stateReg := complete
          }
        }
      }.otherwise {
        when(prevClk & ~io.slave.sclk) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.slave.mosi ## spiShift(p.dataWidth - 1, 1)
              printf("SLAVE TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.slave.mosi
              printf("SLAVE TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := Mux(io.slave.cs, complete, slaveMode)
          }.otherwise {
            stateReg := complete
          }
        }
      }
    }
    is(complete) {
      printf("complete\n")
      shiftCounter := 0.U
      transitionCounter := 1.U
      recieveReg := spiShift
      when(regs.CTRLB(7) === 1.U) { // In Buffer Mode
        recieveBuffer := recieveReg // Buffer will hold older data from recieveReg
        when(regs.INTCTRL(6) === 1.U) { // When Tx Interrupts are enabled
          regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) // Set Tx Done Interrupt
        }
      }
      when(regs.CTRLB(7) === 0.U && regs.INTCTRL(0) === 1.U) { // In Normal mode, when interrupts are enabled
        regs.INTFLAGS := regs.INTFLAGS | (1.U << 7.U) // Set it
      }

      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U) { // In Buffer mode, when transmit buffer still has data
        spiShift := transmitBuffer
        regs.INTFLAGS := regs.INTFLAGS & ~(1.U << 5.U) // Unlock buffer
        writeData := false.B
        when(regs.CTRLA(5) === 1.U) {
          stateReg := masterMode // Even in bufferMode, still need to go to complete state to save data in recieveReg
        }.otherwise {
          stateReg := slaveMode
        }
      }.otherwise {
        stateReg := idle
      }
    }
  }

  // Collect code coverage points
  if (p.coverage) {
    // Cover the entire IO bundle recursively.
    coverAll(io, "_io")
  }

  /** Handles APB register writes. */
  def registerWrite(addr: UInt): Unit = {
    when(addr >= regs.CTRLA_ADDR.U && addr <= regs.CTRLA_ADDR_MAX.U) {
      printf("Writing CTRLA Register, data: %x, addr: %x\n", io.apb.PWDATA, addr)
      regs.CTRLA := io.apb.PWDATA
    }
    when(addr >= regs.CTRLB_ADDR.U && addr <= regs.CTRLB_ADDR_MAX.U) {
      printf("Writing CTRLB Register, data: %x, addr: %x\n", io.apb.PWDATA, addr)
      regs.CTRLB := io.apb.PWDATA
    }
    when(addr >= regs.INTCTRL_ADDR.U && addr <= regs.INTCTRL_ADDR_MAX.U) {
      printf("Writing INTCTRL Register, data: %x, addr: %x\n", io.apb.PWDATA, addr)
      regs.INTCTRL := io.apb.PWDATA
    }
    when(addr >= regs.INTFLAGS_ADDR.U && addr <= regs.INTFLAGS_ADDR_MAX.U) {
      printf("Writing INTFLAGS Register, data: %x, addr: %x\n", io.apb.PWDATA, addr)
      val shiftAddr = (addr - regs.INTFLAGS_ADDR.U)
      regs.INTFLAGS := regs.INTFLAGS & ~io.apb.PWDATA
    }
    when((addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U)) {
      writeData := true.B
      when(regs.CTRLB(7) === 0.U && !(stateReg === masterMode)) { // Can't write during normal transmission
        printf("Writing spiShift Register, data: %x, addr: %x\n", io.apb.PWDATA, addr)
        val shiftAddr = (addr - regs.DATA_ADDR.U)
        spiShift := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(regs.DATA_REG_SIZE - 1, 0) * 8.U))
      }
      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U) { // In buffer mode, when buffer has space
        printf("Writing transmitBuffer, data: %x, addr: %x\n", io.apb.PWDATA, addr)
        val shiftAddr = (addr - regs.DATA_ADDR.U)
        transmitBuffer := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(regs.DATA_REG_SIZE - 1, 0) * 8.U))
        regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Lock Buffer
      }
      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U) { // In buffer mode, when buffer doesn't have space
        regs.INTFLAGS := regs.INTFLAGS | (1.U << 0.U)
      }
    }
  }

  /** Handles APB register reads. */
  def registerRead(addr: UInt): Unit = {
    when(addr >= regs.CTRLA_ADDR.U && addr <= regs.CTRLA_ADDR_MAX.U) {
      printf("Reading CTRLA Register, data: %x, addr: %x\n", regs.CTRLA, addr)
      io.apb.PRDATA := regs.CTRLA
    }
    when(addr >= regs.CTRLB_ADDR.U && addr <= regs.CTRLB_ADDR_MAX.U) {
      printf("Reading CTRLB Register, data: %x, addr: %x\n", regs.CTRLB, addr)
      io.apb.PRDATA := regs.CTRLB
    }
    when(addr >= regs.INTCTRL_ADDR.U && addr <= regs.INTCTRL_ADDR_MAX.U) {
      printf("Reading INTCTRL Register, data: %x, addr: %x\n", regs.INTCTRL, addr)
      io.apb.PRDATA := regs.INTCTRL
    }
    when(addr >= regs.INTFLAGS_ADDR.U && addr <= regs.INTFLAGS_ADDR_MAX.U) {
      printf("Reading INTFLAGS Register, data: %x, addr: %x\n", regs.INTFLAGS, addr)
      io.apb.PRDATA := regs.INTFLAGS
    }
    when(addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U) {
      printf("Reading DATA Register, data: %x, addr: %x\n", recieveReg, addr)
      when(regs.CTRLB(7) === 0.U) {
        io.apb.PRDATA := recieveReg
      }
      when(regs.CTRLB(7) === 1.U) {
        io.apb.PRDATA := recieveBuffer
        recieveBuffer := recieveReg
      }
    }
  }
}