package tech.rocksavage.chiselware.GPIO

import chisel3._
import chisel3.util._
import java.io.{File, PrintWriter}
import _root_.circt.stage.ChiselStage

class ApbInterface(p: BaseParams) extends Bundle {
  val PSEL = Input(Bool()) // Peripheral select
  val PENABLE = Input(Bool()) // Enable signal
  val PWRITE = Input(Bool()) // Read/Write signal
  val PADDR = Input(UInt(p.PADDR_WIDTH.W)) // Address
  val PWDATA = Input(UInt(p.PDATA_WIDTH.W)) // Write data
  val PRDATA = Output(UInt(p.PDATA_WIDTH.W)) // Read data
  val PREADY = Output(Bool()) // Ready signal
  val PSLVERR = Output(Bool()) // Slave error signal
}
