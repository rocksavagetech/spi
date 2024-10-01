package tech.rocksavage.chiselware.SPI

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import chisel3._
import chisel3.util._
import chiseltest._
class SPITest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  
  // Utility function to simulate SPI clock and data shifts
  def simulateSPIClockAndData(dut: SPI, cpol: Boolean, cpha: Boolean, data: UInt): Unit = {
    for (i <- 0 until 8) {
      if (cpol) {
        if (cpha) {
          // CPOL = 1, CPHA = 1: sample on rising edge, change on falling edge
          dut.clock.step(1) // Falling edge, change data
          dut.io.miso.poke(dut.io.mosi.peek()) // Set MISO
          dut.clock.step(1) // Rising edge, sample data
        } else {
          // CPOL = 1, CPHA = 0: sample on falling edge, change on rising edge
          dut.io.miso.poke(dut.io.mosi.peek()) // Set MISO before falling edge
          dut.clock.step(1) // Falling edge, sample data
          dut.clock.step(1) // Rising edge, change data
        }
      } else {
        if (cpha) {
          // CPOL = 0, CPHA = 1: sample on falling edge, change on rising edge
          dut.clock.step(1) // Rising edge, change data
          dut.io.miso.poke(dut.io.mosi.peek()) // Set MISO
          dut.clock.step(1) // Falling edge, sample data
        } else {
          // CPOL = 0, CPHA = 0: sample on rising edge, change on falling edge
          dut.io.miso.poke(dut.io.mosi.peek()) // Set MISO before rising edge
          dut.clock.step(1) // Rising edge, sample data
          dut.clock.step(1) // Falling edge, change data
        }
      }
    }
  }

  "SPI" should "correctly transmit and receive data with CPOL = 0 and CPHA = 0" in {
    test(new SPI(8)) { dut =>
      dut.io.cs.poke(false.B)
      dut.io.cpol.poke(false.B) // CPOL = 0
      dut.io.cpha.poke(false.B) // CPHA = 0
      dut.io.dataIn.poke(31.U)  // Input data: 0b11111
      dut.io.transmit.poke(true.B)

      // Simulate clock and data shifts
      simulateSPIClockAndData(dut, cpol = false, cpha = false, data = 31.U)

      // End transmission
      dut.io.transmit.poke(false.B)
      dut.clock.step(1)

      // Check received data
      dut.io.dataOut.peek().litValue shouldEqual 31
      dut.io.done.peek().litToBoolean shouldEqual true
    }
  }

  it should "correctly transmit and receive data with CPOL = 0 and CPHA = 1" in {
    test(new SPI(8)) { dut =>
      dut.io.cs.poke(false.B)
      dut.io.cpol.poke(false.B) // CPOL = 0
      dut.io.cpha.poke(true.B)  // CPHA = 1
      dut.io.dataIn.poke(31.U)
      dut.io.transmit.poke(true.B)

      // Simulate clock and data shifts
      simulateSPIClockAndData(dut, cpol = false, cpha = true, data = 31.U)

      // End transmission
      dut.io.transmit.poke(false.B)
      dut.clock.step(1)

      // Check received data
      dut.io.dataOut.peek().litValue shouldEqual 31
      dut.io.done.peek().litToBoolean shouldEqual true
    }
  }

  it should "correctly transmit and receive data with CPOL = 1 and CPHA = 0" in {
    test(new SPI(8)) { dut =>
      dut.io.cs.poke(false.B)
      dut.io.cpol.poke(true.B) // CPOL = 1
      dut.io.cpha.poke(false.B) // CPHA = 0
      dut.io.dataIn.poke(31.U)
      dut.io.transmit.poke(true.B)

      // Simulate clock and data shifts
      simulateSPIClockAndData(dut, cpol = true, cpha = false, data = 31.U)

      // End transmission
      dut.io.transmit.poke(false.B)
      dut.clock.step(1)

      // Check received data
      dut.io.dataOut.peek().litValue shouldEqual 31
      dut.io.done.peek().litToBoolean shouldEqual true
    }
  }

  it should "correctly transmit and receive data with CPOL = 1 and CPHA = 1" in {
    test(new SPI(8)) { dut =>
      dut.io.cs.poke(false.B)
      dut.io.cpol.poke(true.B) // CPOL = 1
      dut.io.cpha.poke(true.B)  // CPHA = 1
      dut.io.dataIn.poke(31.U)
      dut.io.transmit.poke(true.B)

      // Simulate clock and data shifts
      simulateSPIClockAndData(dut, cpol = true, cpha = true, data = 31.U)

      // End transmission
      dut.io.transmit.poke(false.B)
      dut.clock.step(1)

      // Check received data
      dut.io.dataOut.peek().litValue shouldEqual 31
      dut.io.done.peek().litToBoolean shouldEqual true
    }
  }
}
