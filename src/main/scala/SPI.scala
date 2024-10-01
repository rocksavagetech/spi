package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

class SPI(val width: Int = 8) extends Module {
  val io = IO(new Bundle {
    val cs       = Input(Bool())        // Chip Select
    val cpol     = Input(Bool())        // Clock Polarity
    val cpha     = Input(Bool())        // Clock Phase
    val dataIn   = Input(UInt(width.W)) // Data to be transmitted
    val transmit = Input(Bool())        // Start transmission
    val mosi     = Output(UInt(1.W))    // Master Out Slave In
    val miso     = Input(UInt(1.W))     // Master In Slave Out
    val clock    = Output(Bool())       // Clock output
    val dataOut  = Output(UInt(width.W))// Received data
    val done     = Output(Bool())       // Transmission complete signal
  })

  // Internal states
  val sIdle :: sTransmit :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val shiftReg = RegInit(0.U(width.W))
  val counter = RegInit(0.U((log2Ceil(width) + 1).W)) // Adjusted counter width
  val clockReg = RegInit(false.B)

  // Outputs
  io.mosi := shiftReg(width - 1) // Send MSB first
  io.dataOut := 0.U
  io.done := false.B
  io.clock := clockReg

  // Always assign default values
  when(io.cs) {
    state := sIdle
    clockReg := io.cpol // Set clock polarity
    io.done := false.B // Reset done when in idle
  } .otherwise {
    switch(state) {
      is(sIdle) {
        when(io.transmit) {
          state := sTransmit
          shiftReg := io.dataIn // Load data to shift register
          counter := 0.U
          clockReg := io.cpol   // Set initial clock state based on CPOL
          io.done := false.B
        }
      }

      is(sTransmit) {
        // Clock toggling based on CPOL and CPHA
        when(counter < width.U) {
          clockReg := !clockReg // Toggle clock

          // Handle data shifting based on CPOL and CPHA
          when(!io.cpha) {
            // CPHA = 0: Sample on the leading clock edge (CPOL flip)
            when(clockReg =/= io.cpol) { // Leading edge (away from CPOL)
              shiftReg := Cat(shiftReg(width - 2, 0), io.miso) // Shift in MISO
              counter := counter + 1.U
            }
          } .otherwise {
            // CPHA = 1: Sample on the trailing clock edge (CPOL match)
            when(clockReg === io.cpol) { // Trailing edge (matching CPOL)
              shiftReg := Cat(shiftReg(width - 2, 0), io.miso) // Shift in MISO
              counter := counter + 1.U
            }
          }
        } .otherwise {
          io.dataOut := shiftReg // Output the received data
          state := sDone
        }
      }

      is(sDone) {
        io.done := true.B // Ensure done signal is driven
        when(!io.transmit) {
          state := sIdle // Reset to idle state
        }
      }
    }
  }

  // Ensure outputs are initialized in all states
  io.done := (state === sDone) // Ensure done signal is driven
  io.clock := clockReg // Ensure clock signal is driven
  io.dataOut := shiftReg // Ensure dataOut is driven based on shiftReg
}
