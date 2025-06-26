package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._
import tech.rocksavage.test.TestUtils.coverAll

/** FullDuplexSPI module for enabling full-duplex SPI communication.
  *
  * This module connects a master and a slave SPI device to support simultaneous data
  * transmission and reception (full-duplex communication).
  *
  * @param p Configuration parameters for the SPI modules (data width, address width, etc.).
  */
class FullDuplexSPI(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    /** IO bundle for FullDuplexSPI, exposing APB and SPI interfaces. */
    val masterApb = new ApbInterface(p)
    val slaveApb  = new ApbInterface(p)
    
    /** Master and Slave SPI interface for external communication. */
    val master = new MasterInterface
    val slave  = new SlaveInterface
  })

  /** Instantiate the SPI master and slave module. */
  val master = Module(new SPI(p))
  val slave  = Module(new SPI(p))

  /** Connect the APB interfaces for configuration and control.
    *
    * - `masterApb` connects to the master SPI device.
    * - `slaveApb` connects to the slave SPI device.
    */
  master.io.apb <> io.masterApb
  slave.io.apb <> io.slaveApb

  /** Establish SPI signal connections for full-duplex communication.
    *
    * - The master drives the clock (SCLK), chip select (CS), and MOSI signals.
    * - The slave provides the MISO signal.
    * - The slave receives SCLK, CS, and MOSI signals from the master.
    * - The master receives the MISO signal from the slave.
    */
  master.io.master <> io.master  
  slave.io.slave <> io.slave    
  slave.io.slave.sclk := master.io.master.sclk  
  slave.io.slave.cs   := master.io.master.cs   
  slave.io.slave.mosi := master.io.master.mosi  

  /** Master receives MISO from the slave **/
  master.io.master.miso := slave.io.slave.miso  

  /** Initialize unused SPI signals to avoid latch inference. */
  master.io.slave.mosi := 0.U
  slave.io.master.miso := 0.U
  master.io.slave.cs := false.B
  master.io.slave.sclk := false.B

  /** Getter method for the master SPI instance.
    *
    * @return The SPI master module instance.
    */  
  def getMaster: SPI = master

  /** Getter method for the slave SPI instance.
    *
    * @return The SPI slave module instance.
    */
  def getSlave: SPI = slave
  
  /** Enable coverage if the `coverage` parameter is set to true.
    *
    * - Covers the entire IO bundle recursively for both master and slave.
    */
  if (p.coverage) {
      coverAll(master.io, "master_io")
      coverAll(slave.io, "slave_io")
  }
          
}
