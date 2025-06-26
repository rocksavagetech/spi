package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._
import java.io.{File, PrintWriter}
import _root_.circt.stage.ChiselStage

/** APB Interface Bundle for SPI communication.
  *
  * This bundle defines the Advanced Peripheral Bus (APB) signals for use
  * in SPI communication modules.
  *
  * @param p Configuration parameters for the SPI (address width, data width, etc.).
  */
class ApbInterface(p: BaseParams) extends Bundle {
  /* val PCLK = Input(Clock())
  val PRESETn = Input(AsyncReset()) 
  */

  /** Peripheral select signal (input). */
  val PSEL = Input(Bool()) 
  /** Enable signal to indicate an active transfer phase (input). */
  val PENABLE = Input(Bool()) 
  /** Read/Write control signal (input). */
  val PWRITE = Input(Bool()) 
  /** Address bus for register access (input). */
  val PADDR = Input(UInt(p.addrWidth.W)) 
  /** Data bus for writes (input). */
  val PWDATA = Input(UInt(p.dataWidth.W)) 
  /** Data bus for reads (output). */
  val PRDATA = Output(UInt(p.dataWidth.W)) 
  /** Ready signal to indicate the peripheral is ready (output). */
  val PREADY = Output(Bool()) 
  /** Slave error signal to indicate an invalid operation (output). */
  val PSLVERR = Output(Bool()) 
}

/** Master SPI Interface Bundle.
  *
  * This bundle defines the SPI signals used by a master device in SPI communication.
  */
class MasterInterface() extends Bundle {
  val miso = Input(Bool())
  val mosi = Output(UInt(1.W))
  val sclk = Output(Bool())
  val cs = Output(Bool())
}

/** Slave SPI Interface Bundle.
  *
  * This bundle defines the SPI signals used by a slave device in SPI communication.
  */
class SlaveInterface() extends Bundle {
  val miso = Output(Bool())
  val mosi = Input(UInt(1.W))
  val sclk = Input(Bool())
  val cs = Input(Bool())
}

/** SPI Interface Bundle.
  *
  * Combines the APB interface, master SPI signals, and slave SPI signals into a single bundle.
  *
  * @param p Configuration parameters for the SPI (address width, data width, etc.).
  */
class SPIInterface(p: BaseParams) extends Bundle {
  val apb = new ApbInterface(p)
  val master = new MasterInterface
  val slave = new SlaveInterface
}

/** Not in use, need to revisit to debug */
/*
class MyApbModule(p: BaseParams) extends RawModule {
  val io = IO(new ApbInterface(p))

  // Reset logic: Invert PRESETn for Chisel's reset type
  val reset_n = (!io.PRESETn.asBool).asAsyncReset

  // Example register with APB interface
  val reg = withClockAndReset(io.PCLK, reset_n)(RegInit(0.U(p.PDATA_WIDTH.W)))

  // Increment logic (example logic for the register)
  when(io.PSEL && io.PENABLE && io.PWRITE) {
    reg := io.PWDATA
  }

  // Outputs
  io.PRDATA := reg
  io.PREADY := true.B
  io.PSLVERR := false.B
}
*/