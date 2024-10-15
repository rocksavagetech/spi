
package tech.rocksavage.chiselware.SPI

import org.scalatest.Assertions._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.coverage._
import chiseltest.simulator.VerilatorCFlags
import firrtl2.options.TargetDirAnnotation

class SPIModuleTest extends AnyFlatSpec with ChiselScalatestTester {
  behavior of "SPIModule"

  val backendAnnotations = Seq(
    // VerilatorBackendAnnotation,
    TargetDirAnnotation("generated")
  )
  val myParams =
    BaseParams(true, 1, 1000, 8, 8)

  def toggleSCLK(cycles: Int = spiClockPeriod / 2): Unit = {
    poke(dut.io.sclk, true.B)
    step(cycles)
    poke(dut.io.sclk, false.B)
    step(cycles)
  }

  // Enable SPI
  poke(dut.io.apb.PSEL, true.B)
  poke(dut.io.apb.PADDR, 0x00.U)  // CTRLA address
  poke(dut.io.apb.PWRITE, true.B)
  poke(dut.io.apb.PWDATA, 0x01.U) // Enable SPI
  poke(dut.io.apb.PENABLE, true.B)
  step(1)

  // Simulate data transfer
  poke(dut.io.mosi, 0xAA.U)  // Send 0xAA on MOSI
  for (i <- 0 until 8) {
    toggleSCLK()  // Toggle SCLK for each bit
  }

  // Check if data was received correctly
  expect(dut.io.miso, 0x55.U)  // Check if 0x55 was received on MISO
  println("Data transfer test passed.")
}

