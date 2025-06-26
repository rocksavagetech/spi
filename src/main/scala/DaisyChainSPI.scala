package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

/** DaisyChainSPI module for connecting multiple SPI slaves in a daisy chain configuration.
  *
  * This module implements a daisy-chained SPI system where a master SPI device communicates
  * with multiple slave SPI devices. The daisy chain allows data to flow from the master
  * through the slaves, with the final slave feeding data back to the master.
  *
  * @param p Configuration parameters for the SPI modules (data width, address width, etc.).
  */
class DaisyChainSPI(p: BaseParams) extends Module {
  /** IO bundle for DaisyChainSPI, exposing APB interfaces and SPI interfaces for the master and slaves. */
  val io = IO(new Bundle {
    /** Master APB interface for configuration and control. */
    val masterApb = new ApbInterface(p)

    /** APB interface for the first slave SPI device. */
    val slave1Apb = new ApbInterface(p)
    /** APB interface for the second slave SPI device. */
    val slave2Apb = new ApbInterface(p)

    /** Master SPI interface for connecting to external devices. */
    val master = new MasterInterface
    /** Slave SPI interface for the first slave. */
    val slave1 = new SlaveInterface
    /** Slave SPI interface for the second slave. */
    val slave2 = new SlaveInterface
  })

  /** Instantiate the SPI master module. */
  val master = Module(new SPI(p))
  /** Instantiate the first SPI slave module. */
  val slave1 = Module(new SPI(p))
  /** Instantiate the second SPI slave module. */
  val slave2 = Module(new SPI(p))

  /** Connect the master APB interface to the master SPI module. */
  master.io.apb <> io.masterApb

  /** Connect the first slave APB interface to the first SPI slave module. */
  slave1.io.apb <> io.slave1Apb
  /** Connect the second slave APB interface to the second SPI slave module. */
  slave2.io.apb <> io.slave2Apb

  /** Connect the master SPI interface to external devices. */
  master.io.master <> io.master 
  /** Connect the first slave SPI interface. */
  slave1.io.slave <> io.slave1 
  /** Connect the second slave SPI interface. */
  slave2.io.slave <> io.slave2 

  /** Configure SPI signal connections for daisy chaining:
    *
    * - The master drives the clock (SCLK) and chip select (CS) signals for all slaves.
    * - The master drives MOSI to the first slave.
    * - Each slave forwards its MISO signal to the next slave's MOSI.
    * - The final slave's MISO is connected back to the master's MISO.
    */

  /** Master to Slave 1 **/
  slave1.io.slave.sclk := master.io.master.sclk
  slave1.io.slave.cs := master.io.master.cs
  slave1.io.slave.mosi := master.io.master.mosi 

  /** Slave 1 to Slave 2 **/
  slave2.io.slave.sclk := master.io.master.sclk
  slave2.io.slave.cs := master.io.master.cs
  slave2.io.slave.mosi := slave1.io.slave.miso 

  /** Slave 2 to Master **/
  master.io.master.miso := slave2.io.slave.miso 

  /** Initialize unused MISO signals to avoid latch inference. */
  master.io.slave.mosi := 0.U
  slave1.io.master.miso := 0.U
  slave2.io.master.miso := 0.U
  master.io.slave.cs := false.B
  master.io.slave.sclk := false.B
}
