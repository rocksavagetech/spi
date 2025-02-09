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

      // Collect code coverage points
  if (p.coverage) {
    // Count clock ticks to allow for coverage computation
    val tick = true.B
    for (bit <- 0 to p.dataWidth - 1) {
      cover(io.masterApb.PRDATA(bit)).suggestName(s"m_apb_PRDATA_$bit")
      cover(io.masterApb.PWDATA(bit)).suggestName(s"m_apb_PWDATA_$bit")
      cover(io.slaveApb.PRDATA(bit)).suggestName(s"s_apb_PRDATA_$bit")
      cover(io.slaveApb.PWDATA(bit)).suggestName(s"s_apb_PWDATA_$bit")
    }
    for (bit <- 0 to p.addrWidth - 1) {
      cover(io.masterApb.PADDR(bit)).suggestName(s"m_apb_ADDR_$bit")
      cover(io.slaveApb.PADDR(bit)).suggestName(s"s_apb_ADDR_$bit")
    }
    cover(tick).suggestName("tick")
    cover(io.masterApb.PSEL).suggestName("m_io__PSEL")
    cover(io.masterApb.PENABLE).suggestName("m_io__PENABLE")
    cover(io.masterApb.PWRITE).suggestName("m_io__PWRITE")
    cover(io.masterApb.PREADY).suggestName("m_io__PREADY")
    cover(io.masterApb.PSLVERR).suggestName("m_io__PSLVERR")
    cover(io.slaveApb.PSEL).suggestName("s_io__PSEL")
    cover(io.slaveApb.PENABLE).suggestName("s_io__PENABLE")
    cover(io.slaveApb.PWRITE).suggestName("s_io__PWRITE")
    cover(io.slaveApb.PREADY).suggestName("s_io__PREADY")
    cover(io.slaveApb.PSLVERR).suggestName("s_io__PSLVERR")
    cover(io.master.sclk).suggestName(s"io_m_sclk")
    cover(io.master.miso).suggestName(s"io_m_miso")
    cover(io.master.mosi.asBool).suggestName(s"io_m_mosi")
    cover(io.master.cs).suggestName(s"io_m_cs")
    cover(io.slave.sclk).suggestName(s"io_s_sclk")
    cover(io.slave.miso).suggestName(s"io_s_miso")
    cover(io.slave.mosi.asBool).suggestName(s"io_s_mosi")
    cover(io.slave.cs).suggestName(s"io_s_cs")
  }

}
