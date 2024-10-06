package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPISlave(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)
    val pins = new Bundle {
      val miso = Output(UInt(1.W))
      val mosi = Input(UInt(1.W))
      val sclk = Input(Clock())
      val cs = Input(UInt(1.W))
    }
  })
  val spiReg = RegInit(0.U(p.dataWidth.W))
  val sclkReg = RegInit(0.U)

}
