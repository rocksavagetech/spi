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
  val spiRegTransmit = RegInit(0.U(p.dataWidth.W))
  val spiRegRecieve = RegInit(0.U(p.dataWidth.W))
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))
  val sclkReg = RegInit(false.B)
  val prevClk = RegInit(false.B)

  object State extends ChiselEnum {
    val IDLE, DUPLEX = Value
  }
  import State._

  val stateReg = RegInit(IDLE)
  io.apb.PRDATA := 0.U

  switch(stateReg) {
    is(IDLE) {
      printf("IDLE\n")
      spiRegTransmit := 0.U
      shiftCounter := 0.U
      when(io.apb.PSEL && io.apb.PENABLE) {
        when(io.apb.PWRITE) {
          spiRegRecieve := 0.U
          spiRegTransmit := io.apb.PWDATA
          stateReg := DUPLEX
        }.otherwise {
          io.apb.PRDATA := spiRegRecieve
          stateReg := IDLE
        }
      }.otherwise {
        stateReg := IDLE
      }
    }
    is(DUPLEX) {
      when((p.spiMode == 1).B || (p.spiMode == 4).B) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth).U) {
            spiRegTransmit := 0.U ## spiRegTransmit(p.dataWidth - 1, 1)
            spiRegRecieve := (spiRegRecieve >> 1) | (io.pins.miso << (p.dataWidth - 1))
            printf("TRANSMIT: %x\n", spiRegTransmit(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := DUPLEX
          }.otherwise {
            stateReg := IDLE
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth).U) {
            spiRegTransmit := 0.U ## spiRegTransmit(p.dataWidth - 1, 1)
            spiRegRecieve := (spiRegRecieve >> 1) | (io.pins.miso << (p.dataWidth - 1))
            printf("TRANSMIT: %x\n", spiRegTransmit(0))
            shiftCounter := shiftCounter + 1.U
            stateReg := DUPLEX
          }.otherwise {
            stateReg := IDLE
          }
        }
      }
    }
  }
  io.pins.mosi := spiRegTransmit(0)
  io.apb.PREADY := (io.apb.PENABLE && io.apb.PSEL)
  io.pins.cs := (stateReg === IDLE)
  when(io.apb.PSEL && io.apb.PENABLE && (stateReg === DUPLEX)) {
    io.apb.PSLVERR := 1.U
  }.otherwise {
    io.apb.PSLVERR := 0.U
  }

}
