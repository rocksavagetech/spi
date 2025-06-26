package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._
import tech.rocksavage.test.TestUtils.coverAll

/** SPI (Serial Peripheral Interface) module.
  *
  * This module implements an SPI master and slave interface with configurable parameters.
  * It supports both master and slave modes, with control registers for configuration,
  * and handles data transmission and reception.
  *
  * @param p Configuration parameters for the SPI module.
  */
class SPI(p: BaseParams) extends Module {
  /** Input/output bundle for the SPI module.
    *
    * Includes the following interfaces:
    * - APB interface for register access.
    * - Master interface for SPI communication.
    * - Slave interface for SPI communication.
    */
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)   
    val master = new MasterInterface 
    val slave = new SlaveInterface   
  })

  /** Control registers for SPI configuration and status. */
  val regs = new SPIRegs(p)

  /** Shift register for SPI data transmission and reception. */
  val spiShift = RegInit(0.U(p.dataWidth.W))

  /** Counter to track the number of bits shifted. */
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))

  /** Flag to indicate when data is written to the transmit buffer. */
  val writeData = RegInit(false.B)

  /** Buffer for holding data to be transmitted. */
  val transmitBuffer = RegInit(0.U(p.dataWidth.W))

  /** Buffer for holding received data. */
  val recieveBuffer = RegInit(0.U(p.dataWidth.W))

  /** Register to hold the most recently received data. */
  val recieveReg = RegInit(0.U(p.dataWidth.W))
  val dataOrder = RegInit(0.U(2.W))

  /** Enumeration for SPI state machine states. */
  object State extends ChiselEnum {
    val idle, masterMode, slaveMode, complete = Value
  }
  import State._

  /** State register for the SPI state machine. */
  val stateReg = RegInit(idle)

  /** Register for the SPI master clock signal. */
  val sclkReg = RegInit(false.B)

  /** Register to store the previous clock state for edge detection. */
  val prevClk = RegInit(false.B)

  /** Counter for generating the SPI master clock. */
  val sclkCounter = RegInit(0.U(8.W))
  val transitionCounter = RegInit(0.U(8.W))

  /** Implements the logic for SPI Master Mode.
    *
    * In this mode:
    * - Data is transmitted via the `mosi` signal, with bit order determined by the control register.
    * - The slave `miso` signal is disabled.
    * - The SPI clock (`sclk`) is generated based on the control register settings.
    */
  when(regs.CTRLA(5) === 1.U) { 
    /** Determine the bit order for data transmission. */
    when(regs.CTRLA(6) === 0.U) {
      io.master.mosi := spiShift(p.dataWidth - 1)
    }.otherwise {
      io.master.mosi := spiShift(0)
    }

    /** Assert chip select signal when in master mode. */
    io.master.cs := ~(stateReg === masterMode)

    /** Disable `miso` signal in master mode. */
    io.slave.miso := 0.U 

    /** Generate SPI clock signal. */
    when(sclkCounter === (((2.U << (regs.CTRLA(2, 1) * 2.U)) >> (regs.CTRLA(4))) - 1.U)) {
      sclkReg := ~sclkReg
      transitionCounter := transitionCounter + 1.U
      sclkCounter := 0.U
    }.otherwise {
      sclkCounter := sclkCounter + 1.U
    }

    /** Assign the generated SPI clock signal. */
    io.master.sclk := Mux(transitionCounter <= ((p.dataWidth.U >> regs.CTRLB(3, 2)) << 1.U),
    sclkReg,
    !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))
    )
    }.otherwise {
    /** Disable master signals when in slave mode. */
    io.master.sclk := 0.U 
    io.master.mosi := 0.U 
    io.master.cs := 0.U   

    /** Enable `miso` signal in slave mode based on bit order. */
    when(regs.CTRLA(6) === 0.U) {
      io.slave.miso := spiShift(p.dataWidth - 1)
    }.otherwise {
      io.slave.miso := spiShift(0)
    }
  }

  /** Handles APB register writes and reads.
    *
    * Reads and writes are performed based on the `PSEL`, `PENABLE`, and `PWRITE` signals.
    */  
  io.apb.PRDATA := 0.U
  when(io.apb.PSEL && io.apb.PENABLE) {
    when(io.apb.PWRITE) {
      registerWrite(io.apb.PADDR)
    }.otherwise {
      registerRead(io.apb.PADDR)
    }
  }

  /** Signal to indicate APB transaction readiness. */
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  /** Handles invalid APB address cases. */
  when((io.apb.PADDR < regs.CTRLA_ADDR.U) || (io.apb.PADDR > regs.DATA_ADDR_MAX.U)) {
    io.apb.PSLVERR := true.B 
  }.otherwise {
    io.apb.PSLVERR := false.B 
  }

  /** Handles write collision errors in master mode.
    *
    * A write collision occurs when the transmit buffer is written to while a transmission is ongoing.
    */
  when((writeData) && (stateReg === masterMode)) {
    when(regs.CTRLB(7) === 0.U) {
      regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) 
    }
  }

/** State machine logic for managing SPI operations.
  *
  * The SPI state machine transitions between four states:
  * - **idle**: Initial state where the SPI interface awaits configuration or data.
  * - **masterMode**: Transmitting or receiving data in master mode.
  * - **slaveMode**: Responding to commands in slave mode.
  * - **complete**: Final state indicating the end of a transmission cycle.
  */
  switch(stateReg) {
    /** Idle state of the SPI interface.
      *
      * Resets counters and determines the next state based on control registers:
      * - Master mode if `CTRLA[5]` is set.
      * - Slave mode if `CTRLA[5]` is cleared.
      */
    is(idle) {
      shiftCounter := 0.U
      transitionCounter := 0.U
      dataOrder := Mux(p.dataWidth.U === 32.U, regs.CTRLB(3, 2) + 1.U, regs.CTRLB(3, 2))
      
      /** Master Mode **/
      when(regs.CTRLA(5) === 1.U) {
        io.master.sclk := !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))        
        sclkReg := !((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b01".U))

        /** DATA register is written to, and SPI is enabled **/
        when((writeData) && (regs.CTRLA(0) === 1.U)) { 
          /** In Buffer mode, when buffer has data **/
          when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U) {
            spiShift := transmitBuffer
            /** Buffer can be overriden now **/
            regs.INTFLAGS := regs.INTFLAGS & ~(1.U << 5.U) 
          }
          writeData := false.B
          io.master.cs := false.B
          stateReg := masterMode
        }.otherwise {
          stateReg := idle
        }

        /** Slave Mode **/
      }.otherwise { 
        when(~io.slave.cs && (regs.CTRLA(0) === 1.U)) {
          when(regs.CTRLB(7) === 1.U) {
            spiShift := transmitBuffer
            /** Buffer can be overriden now **/
            regs.INTFLAGS := regs.INTFLAGS | (1.U << 5.U) 
          }
          writeData := false.B
          stateReg := slaveMode
        }.otherwise {
          stateReg := idle
        }
      }
    }

    /** Master mode operation state.
      *
      * Handles data transmission and reception in master mode. Sampling occurs
      * on either the rising or falling edge of the clock, determined by `CTRLB[1:0]`.
      *
      * Transitions to the `complete` state when all bits are transmitted.
      */
    is(masterMode) {
      prevClk := sclkReg
      /** Rising Edge Sampling **/
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) {
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth.U >> regs.CTRLB(3, 2))) {
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
      /** Falling Edge Sampling **/
      }.otherwise {
        when(prevClk & ~sclkReg) { 
          when(shiftCounter < (p.dataWidth.U >> regs.CTRLB(3, 2))) {
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
    /** Slave mode operation state.
      *
      * Handles data transmission and reception in slave mode. Sampling occurs
      * on either the rising or falling edge of the clock, determined by `CTRLB[1:0]`.
      *
      * Transitions to the `complete` state when all bits are transmitted or
      * when the slave is deselected (`cs` signal is high).
      */
    is(slaveMode) {
      prevClk := io.slave.sclk
      when(io.slave.cs){
        stateReg := complete
      }
      /** Rising Edge Sampling **/
      when((regs.CTRLB(1, 0) === "b00".U) || (regs.CTRLB(1, 0) === "b11".U)) { 
        when(~prevClk & io.slave.sclk) {
          when(shiftCounter < (p.dataWidth.U >> regs.CTRLB(3, 2))) {
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
        /** Falling Edge Sampling **/
        when(prevClk & ~io.slave.sclk) { 
          when(shiftCounter < (p.dataWidth.U >> regs.CTRLB(3, 2))) {
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
    /** Complete state of the SPI interface.
      *
      * Finalizes the data transaction and handles post-transaction activities such as:
      * - Buffering received data.
      * - Generating interrupts if enabled.
      *
      * Transitions to the idle state if no more data is available or to master/slave
      * mode if additional data needs processing.
      */
    is(complete) {
      printf("complete\n")
      shiftCounter := 0.U
      transitionCounter := 1.U
      recieveReg := spiShift
      /** Buffer Mode **/
      when(regs.CTRLB(7) === 1.U) { 
        recieveBuffer := recieveReg
        /** Set Tx Interrupts**/
        when(regs.INTCTRL(6) === 1.U) { 
          regs.INTFLAGS := regs.INTFLAGS | (1.U << 6.U) 
        }
      }
      /** Normal Mode **/
      when(regs.CTRLB(7) === 0.U && regs.INTCTRL(0) === 1.U) { 
        regs.INTFLAGS := regs.INTFLAGS | (1.U << 7.U) 
      }

      /** Buffer mode, when transmit buffer still has data**/
      when(regs.CTRLB(7) === 1.U && regs.INTFLAGS(5) === 1.U && dataOrder === 0.U) { 
        spiShift := transmitBuffer
        dataOrder := Mux(p.dataWidth.U === 32.U, regs.CTRLB(3, 2) + 1.U, regs.CTRLB(3, 2))
        /** Unlock Buffer **/
        regs.INTFLAGS := regs.INTFLAGS & ~(1.U << 5.U) 
        writeData := false.B
        stateReg := Mux(regs.CTRLA(5) === 1.U, masterMode, slaveMode)
      }.otherwise {
        when((dataOrder =/= 0.U) && (regs.CTRLB(4) === 0.U)) {
          dataOrder := dataOrder - 1.U
          stateReg := Mux(regs.CTRLA(5) === 1.U, masterMode, slaveMode)
        }.otherwise {
          stateReg := idle
        }
      }
    }
  }

  /** Collects code coverage points for verification. */
  if (p.coverage) {
    coverAll(io, "_io")
  }

  /** Handles APB register writes.
    *
    * @param addr The address of the register being written to.
    */
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

  /** Handles APB register reads.
    *
    * @param addr The address of the register being read.
    */
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