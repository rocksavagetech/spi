package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class FullDuplexSPI(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    // Expose the master and slave APB interfaces
    val masterApb = new ApbInterface(p)
    val slaveApb  = new ApbInterface(p)
    
    // Expose the master and slave SPI interfaces for Full Duplex operation
    val master = new MasterInterface
    val slave  = new SlaveInterface
  })

  // Instantiate the SPI master and slave 
  val master = Module(new SPI(p))
  val slave  = Module(new SPI(p))

  // Connect the APB interface to both master and slave
  master.io.apb <> io.masterApb
  slave.io.apb <> io.slaveApb

  // Connect the SPI signals for Full Duplex
  // Master -> Slave
  master.io.master <> io.master  // Master drives SCLK, MOSI, and CS
  slave.io.slave <> io.slave    // Slave drives MISO
  slave.io.slave.sclk := master.io.master.sclk  // Slave gets the SCLK from master
  slave.io.slave.cs   := master.io.master.cs    // Slave gets CS from master
  
  slave.io.slave.mosi := master.io.master.mosi  // Slave gets MOSI from master

  // Slave -> Master
  master.io.master.miso := slave.io.slave.miso  // Master gets MISO from slave

  master.io.slave.mosi := 0.U
  slave.io.master.miso := 0.U
  master.io.slave.cs := false.B
  master.io.slave.sclk := false.B

  // Getter methods to access internal SPI modules
  def getMaster: SPI = master
  def getSlave: SPI = slave
}
