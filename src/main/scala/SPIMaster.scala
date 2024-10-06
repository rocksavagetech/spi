package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPIMaster(p: BaseParams) extends Module {
  val io = IO(new Bundle {
    val apb = new ApbInterface(p)
    val pins = new Bundle {
      val miso = Input(UInt(1.W))
      val mosi = Output(UInt(1.W))
      val sclk = Output(Bool())
      // val cs = Output(UInt(1.W))
    }
  })
  val spiRegTransmit = RegInit(0.U(p.dataWidth.W))
  val spiRegRecieve = RegInit(0.U(p.dataWidth.W))
  val shiftCounter = RegInit(0.U((log2Ceil(p.dataWidth) + 1).W))
  val sclkReg = RegInit(false.B)
  val prevClk = RegInit(false.B)

  // Clock generation
  val sclkCounter = RegInit(0.U(log2Ceil(p.clockFreq).W))
  when(p.spiMaster.B) {
    when(sclkCounter === (p.clockFreq / 2 - 1).U) {
      prevClk := sclkReg
      sclkReg := ~sclkReg
      sclkCounter := 0.U
    }.otherwise {
      sclkCounter := sclkCounter + 1.U
    }
  }
  io.pins.sclk := sclkReg
  object State extends ChiselEnum {
    val IDLE, SELECT, TRANSMIT, RECIEVE, COMPLETE = Value
  }
  import State._

  val stateReg = RegInit(IDLE)

  switch(stateReg) {
    is(IDLE) {
      shiftCounter := 0.U
      when((p.spiMode == 1).B || (p.spiMode == 2).B) { // IDLE CLK = LOW
        io.pins.sclk := false.B
      }.otherwise { // IDLE CLK = HIGH
        io.pins.sclk := true.B
      }
      when(io.apb.PSEL && io.apb.PENABLE) {
        when(io.apb.PWRITE) {
          stateReg := TRANSMIT
          spiRegTransmit := io.apb.PWDATA
          io.apb.PREADY := true.B
        }.otherwise {
          stateReg := RECIEVE
          io.apb.PREADY := true.B
        }
      }.otherwise {
        stateReg := IDLE
      }
    }
    is(TRANSMIT) {
      when((p.spiMode == 1).B || (p.spiMode == 4).B) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth - 1).U) {
            spiRegTransmit := 0.U ## spiRegTransmit(p.dataWidth - 1, 1)
            // io.pins.mosi := spiRegTransmit(0)
            shiftCounter := shiftCounter + 1.U
            stateReg := TRANSMIT
          }.otherwise {
            stateReg := COMPLETE
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth - 1).U) {
            spiRegTransmit := 0.U ## spiRegTransmit(p.dataWidth - 1, 1)
            // io.pins.mosi := spiRegTransmit(0)
            shiftCounter := shiftCounter + 1.U
            stateReg := TRANSMIT
          }.otherwise {
            stateReg := COMPLETE
          }
        }
      }
    }
    is(RECIEVE) {
      when((p.spiMode == 1).B || (p.spiMode == 4).B) { // Sample on Rising Edge
        when(~prevClk & sclkReg) {
          when(shiftCounter < (p.dataWidth - 1).U) {
            spiRegRecieve := (spiRegRecieve >> 1) | (io.pins.miso << (p.dataWidth - 1))
            shiftCounter := shiftCounter + 1.U
            stateReg := RECIEVE
          }.otherwise {
            // io.apb.PRDATA := spiRegRecieve
            stateReg := COMPLETE
          }
        }
      }.otherwise {
        when(prevClk & ~sclkReg) { // Sample on Falling Edge
          when(shiftCounter < (p.dataWidth - 1).U) {
            spiRegRecieve := (spiRegRecieve >> 1) | (io.pins.miso << (p.dataWidth - 1))
            shiftCounter := shiftCounter + 1.U
            stateReg := RECIEVE
          }.otherwise {
            // io.apb.PRDATA := spiRegRecieve
            stateReg := COMPLETE
          }
        }
      }
    }
    is(COMPLETE) {
      // io.apb.PSLVERR := 0.U
      spiRegTransmit := 0.U
      spiRegRecieve := 0.U
      stateReg := IDLE
    }
  }
  io.pins.mosi := spiRegTransmit(0)
  io.apb.PRDATA := spiRegRecieve
  io.apb.PREADY := false.B

}
