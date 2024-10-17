package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPI(p: BaseParams) extends Module {
  val io = IO(new SPIInterface(p))
  
  // Control Registers
  val regs = new SPIRegs(p)

  // Shift Register
  val spiShift = RegInit(0.U(p.dataWidth.W))
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))

  // Internal Flags
  val writeData = RegInit(false.B)

  // Transmit and Recieve Buffer
  val transmitBuffer = RegInit(0.U(p.dataWidth.W))
  val recieveBuffer = RegInit(0.U(p.dataWidth.W))
  val recieveReg = RegInit(
    0.U(p.dataWidth.W)
  ) // Recieve has one reg buffer even in Normal Mode

  // State Machine Initialization
  object State extends ChiselEnum {
    val idle, masterMode, slaveMode, complete = Value
  }
  import State._
  val stateReg = RegInit(idle)

  // Master Clock Generation & MOSI Control
  val sclkReg = RegInit(false.B)
  val prevClk = RegInit(false.B)
  val sclkCounter = RegInit(0.U(8.W))
  when(regs.CTRLA(5) === 1.U) { // Master Mode Enabled.
    when (regs.CTRLA(6) === 0.U){
      io.master.mosi := spiShift(p.dataWidth - 1)
    }.otherwise{
      io.master.mosi := spiShift(0)
    }
    io.master.cs := (stateReg === idle)
    io.slave.miso := 0.U // MISO Off in Master Mode

    val baseValue = 4.U
    val powerValue = regs.CTRLA(2, 1)  // Assuming this is the power you want to raise to
    val prescalerValue = RegInit(1.U(32.W))    // Initialize the prescalerValue register
    
    val counter = RegInit(0.U(8.W))    // Counter to keep track of the loop
    val maxIterations = powerValue     // The loop should run powerValue times
    
    // Loop to multiply the prescalerValue by baseValue 'powerValue' times
    when(counter < maxIterations) {
      prescalerValue := prescalerValue * baseValue     // Multiply the prescalerValue by the base value
      counter := counter + 1.U         // Increment the counter
    }
    val clk2xValue =  Mux(
              regs.CTRLA(4) === 1.U,
              2.U,
              1.U)   

    val toggleThreshold = (prescalerValue / clk2xValue) - 1.U

    when(sclkCounter === toggleThreshold) {
      prevClk := sclkReg
      sclkReg := ~sclkReg
      sclkCounter := 0.U
    }.otherwise {
      sclkCounter := sclkCounter + 1.U
    }
    io.master.sclk := sclkReg
  }.otherwise {
    io.master.sclk := 0.U // Master clk off in slave mode
    io.master.mosi := 0.U // MOSI off in slave Mode
    io.master.cs := 0.U // Master CS off in slave mode
    when(regs.CTRLA(6) === 0.U){
      io.slave.miso := spiShift(p.dataWidth - 1)
    }.otherwise{
      io.slave.miso := spiShift(0)
    }
  }

  io.apb.PRDATA := 0.U
  // Control Register Read/Write
  when(io.apb.PSEL && io.apb.PENABLE) {
    when(io.apb.PWRITE) {
      registerWrite(io.apb.PADDR)
    }.otherwise {
      registerRead(io.apb.PADDR)
    }
  }

  // APB Signal Control
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  // Handle invalid address case
  when(
    (io.apb.PADDR < regs.CTRLA_ADDR.U) || (io.apb.PADDR > regs.DATA_ADDR_MAX.U)
  ) {
    io.apb.PSLVERR := true.B // Set error signal
  }.otherwise {
    io.apb.PSLVERR := false.B // Clear error signal if valid
  }

  // Error Handling
  when((writeData) && (stateReg === masterMode)) {
    when(regs.CTRLB(7) === 0.U) { // In Normal Mode
      regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) // Write Collision
    }
  }

  // Notes:
  // Implement wait for recieve buffer functionality
  // Might need to fix master.cs functionality
  // Might need to clear buffers/regs when switching between master and slave

  switch(stateReg) {
    is(idle) {
      printf("idle\n")
      shiftCounter := 0.U
      when(regs.CTRLA(5) === 1.U) { // Master Mode
        sclkReg := !((regs
          .CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))
        when((writeData) && (regs.CTRLA(0) === 1.U)) { // When the DATA register is written to and SPI is enabled
          when(regs.CTRLB(7) === 1.U & regs.INTCTRL(5) === 1.U) { // In Buffer mode, when buffer has space
            spiShift := transmitBuffer
            regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Buffer can be overriden now
          }
          writeData := false.B
          stateReg := masterMode
        }.otherwise {
          stateReg := idle
        }
      }.otherwise { // Slave Mode
        when(~io.slave.cs && (regs.CTRLA(0) === 1.U)) { // When slave is selected and SPI is enabled
          when(regs.CTRLB(7) === 1.U & regs.INTCTRL(5) === 1.U) {
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
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { // Sample on Rising Edge
        when(~prevClk & sclkReg & sclkCounter === 0.U) {
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
            when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U & regs.INTCTRL(5) === 1.U) { // In Buffer mode, when transmit buffer still has data
              printf("Buffer Mode\n")
              shiftCounter := 0.U
              spiShift := transmitBuffer
              regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Unlock buffer
              stateReg := masterMode
              writeData := false.B
            }.otherwise {
              stateReg := complete
            }
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg & sclkCounter === 0.U) { // Sample on Falling Edge
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
            when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U  & regs.INTCTRL(5) === 1.U) { // In Buffer mode, when transmit buffer still has data
              shiftCounter := 0.U
              spiShift := transmitBuffer
              regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Unlock buffer
              stateReg := masterMode
              writeData := false.B
            }.otherwise {
              stateReg := complete
            }
          }
        }
      }
    }
    is(slaveMode) {
      prevClk := io.slave.sclk
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { // Sample on Rising Edge
        when(~prevClk & io.slave.sclk & sclkCounter === 0.U) {
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.slave.mosi ## spiShift(p.dataWidth - 1, 1)
              printf("SLAVE TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.slave.mosi
              printf("SLAVE TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := Mux(
              io.slave.cs,
              idle,
              slaveMode
            ) // When cs goes high, abondon all transmissions and go back to idle
          }.otherwise {
            when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U  & regs.INTCTRL(5) === 1.U) { // In Buffer mode, when transmit buffer still has data
              shiftCounter := 0.U
              spiShift := transmitBuffer
              regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Unlock buffer
              stateReg := Mux(io.slave.cs, idle, slaveMode)
              writeData := false.B
            }.otherwise {
              stateReg := complete
            }
          }
        }
      }.otherwise {
        when(prevClk & ~io.slave.sclk & sclkCounter === 0.U) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            when(regs.CTRLA(6) === 1.U) {
              spiShift := io.slave.mosi ## spiShift(p.dataWidth - 1, 1)
              printf("SLAVE TRANSMIT: %x\n", spiShift(0))
            }.otherwise {
              spiShift := spiShift(p.dataWidth - 2, 0) ## io.slave.mosi
              printf("SLAVE TRANSMIT: %x\n", spiShift(p.dataWidth - 1))
            }
            shiftCounter := shiftCounter + 1.U
            stateReg := Mux(io.slave.cs, idle, slaveMode)
          }.otherwise {
            when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U) { // In Buffer mode, when transmit buffer still has data
              shiftCounter := 0.U
              spiShift := transmitBuffer
              regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) // Unlock buffer
              stateReg := Mux(io.slave.cs, idle, slaveMode)
              writeData := false.B
            }.otherwise {
              stateReg := complete
            }
          }
        }
      }
    }
    is(complete) {
      printf("complete\n")
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
      regs.INTFLAGS := regs.INTFLAGS & ~(io.apb.PWDATA(
        regs.INTFLAGS_SIZE - 1,
        0
      ) << (shiftAddr(
        regs.INTFLAGS_REG_SIZE - 1,
        0
      ) * 8.U)) // Writing a 1 will clear the interrupt status
    }
    when((addr >= regs.DATA_ADDR.U && addr <= regs.DATA_ADDR_MAX.U)) {
      writeData := true.B
      when(regs.CTRLB(7) === 0.U && !(stateReg === masterMode)) { // Can't write during normal transmission
        printf(
          "Writing spiShift Register, data: %x, addr: %x\n",
          io.apb.PWDATA,
          addr
        )
        val shiftAddr = (addr - regs.DATA_ADDR.U)
        spiShift := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(
          regs.DATA_REG_SIZE - 1,
          0
        ) * 8.U))
      }
      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 0.U) { // Buffer is full
        regs.INTFLAGS := regs.INTFLAGS | (1.U) // Buffer can be overriden now
      }
      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U) { // In buffer mode, when buffer has space
        printf(
          "Writing transmitBuffer, data: %x, addr: %x\n",
          io.apb.PWDATA,
          addr
        )
        writeData := true.B
        val shiftAddr = (addr - regs.DATA_ADDR.U)
        transmitBuffer := (io.apb.PWDATA(regs.DATA_SIZE - 1, 0) << (shiftAddr(
          regs.DATA_REG_SIZE - 1,
          0
        ) * 8.U))
        regs.INTFLAGS := regs.INTFLAGS & ~(1.U << 5.U) // Lock Buffer
      }
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
      when(regs.CTRLB(7) === 0.U) {
        io.apb.PRDATA := recieveReg
      }
      when(regs.CTRLB(7) === 1.U) {
        io.apb.PRDATA := recieveBuffer
        recieveBuffer := recieveReg
        // when(regs.INTCTRL(7)) Implement recieve buffer empty and overflow interrupt
      }
    }
  }

}
