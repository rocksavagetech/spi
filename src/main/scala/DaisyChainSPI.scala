package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class DaisyChainSPI(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    // Expose the master APB interface
    val masterApb = new ApbInterface(p)

    // Expose slave APB interfaces
    val slave1Apb = new ApbInterface(p)
    val slave2Apb = new ApbInterface(p)
    //val slave3Apb = new ApbInterface(p)

    // Expose the master and slave SPI interfaces for Full Duplex operation
    val master = new MasterInterface
    val slave1 = new SlaveInterface
    val slave2 = new SlaveInterface
   // val slave3 = new SlaveInterface
  })

// Instantiate the SPI master and slaves
  val master = Module(new SPI(p))
  val slave1 = Module(new SPI(p))
  val slave2 = Module(new SPI(p))
  //val slave3 = Module(new SPI(p))

// Connect the master APB interface
  master.io.apb <> io.masterApb

// Connect the slave APB interfaces
  slave1.io.apb <> io.slave1Apb
  slave2.io.apb <> io.slave2Apb
  //slave3.io.apb <> io.slave3Apb

  master.io.master <> io.master // Master drives SCLK, MOSI, and CS
  slave1.io.slave <> io.slave1 // Slave drives MISO
  slave2.io.slave <> io.slave2 // Slave drives MISO
  //slave3.io.slave <> io.slave3 // Slave drives MISO

// Connect the SPI signals for daisy chaining
// Master to Slave 1
  slave1.io.slave.sclk := master.io.master.sclk
  slave1.io.slave.cs := master.io.master.cs
  slave1.io.slave.mosi := master.io.master.mosi // Master drives MOSI to Slave 1

// Slave 1 to Slave 2
  slave2.io.slave.sclk := master.io.master.sclk
  slave2.io.slave.cs := master.io.master.cs
  slave2.io.slave.mosi := slave1.io.slave.miso // Slave 1 drives MOSI to Slave 2

// Slave 2 to Slave 3
  //slave3.io.slave.sclk := master.io.master.sclk
  //slave3.io.slave.cs := master.io.master.cs
  //slave3.io.slave.mosi := slave2.io.slave.miso // Slave 2 drives MOSI to Slave 3

// Master gets MISO from Slave 3
  master.io.master.miso := slave2.io.slave.miso // Final MISO connection back to Master

// Initialize unused MISO signals to avoid latch inference
  master.io.slave.mosi := 0.U
  slave1.io.master.miso := 0.U
  slave2.io.master.miso := 0.U
  //slave3.io.master.miso := 0.U
  master.io.slave.cs := false.B
  master.io.slave.sclk := false.B
  
}
