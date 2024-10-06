/*
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

  // Helper function to run the master-slave transmission
  def runMasterTransmission(dut: SPIMaster): Unit = {
    // Write data from APB to SPI
    dut.io.apb.PWDATA.poke(0xa5.U)
    dut.io.apb.PSEL.poke(true.B)
    dut.io.apb.PWRITE.poke(true.B)
    dut.io.apb.PENABLE.poke(true.B)
    // Check if data is being shifted out correctly on MOSI
    for (i <- 7 until -1) {
      dut.clock.step(1)
      println(s"MOSI: ${dut.io.pins.mosi.peek().litValue}")
      dut.io.pins.mosi.expect(0xa5.U & (1 << i).U)
    }
  }

  // Tests for Mode 0
  it should "work as an SPI master in Mode0" in {
    test(new SPIMaster(myParams))
      .withAnnotations(backendAnnotations) { dut =>
        test(new SPI(myParams, 1000000, SPIMode.Mode0, SPIRole.Master))
      }
  }

  it should "work as an SPI slave in Mode0" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode0, SPIRole.Slave))
      .withAnnotations(backendAnnotations) { dut =>
        for (i <- 0 until 8) {
          dut.io.spi.mosi.poke((i % 2 == 0).B)
          dut.io.spi.sclk.poke(false.B)
          dut.clock.step(1)
          dut.io.spi.sclk.poke(true.B)
          dut.clock.step(1)
        }
        dut.io.apb.PRDATA.expect(0xaa.U)
      }
  }

  // Tests for Mode 1
  it should "work as an SPI master in Mode1" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode1, SPIRole.Master))
      .withAnnotations(backendAnnotations) { dut =>
        runMasterTransmission(dut, SPIMode.Mode1)
      }
  }

  it should "work as an SPI slave in Mode1" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode1, SPIRole.Slave))
      .withAnnotations(backendAnnotations) { dut =>
        for (i <- 0 until 8) {
          dut.io.spi.mosi.poke((i % 2 == 0).B)
          dut.io.spi.sclk.poke(true.B)
          dut.clock.step(1)
          dut.io.spi.sclk.poke(false.B)
          dut.clock.step(1)
        }
        dut.io.apb.PRDATA.expect(0xaa.U)
      }
  }

  // Tests for Mode 2
  it should "work as an SPI master in Mode2" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode2, SPIRole.Master))
      .withAnnotations(backendAnnotations) { dut =>
        runMasterTransmission(dut, SPIMode.Mode2)
      }
  }

  it should "work as an SPI slave in Mode2" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode2, SPIRole.Slave))
      .withAnnotations(backendAnnotations) { dut =>
        for (i <- 0 until 8) {
          dut.io.spi.mosi.poke((i % 2 == 0).B)
          dut.io.spi.sclk.poke(false.B)
          dut.clock.step(1)
          dut.io.spi.sclk.poke(true.B)
          dut.clock.step(1)
        }
        dut.io.apb.PRDATA.expect(0xaa.U)
      }
  }

  // Tests for Mode 3
  it should "work as an SPI master in Mode3" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode3, SPIRole.Master))
      .withAnnotations(backendAnnotations) { dut =>
        runMasterTransmission(dut, SPIMode.Mode3)
      }
  }

  it should "work as an SPI slave in Mode3" in {
    test(new SPI(myParams, 1000000, SPIMode.Mode3, SPIRole.Slave))
      .withAnnotations(backendAnnotations) { dut =>
        for (i <- 0 until 8) {
          dut.io.spi.mosi.poke((i % 2 == 0).B)
          dut.io.spi.sclk.poke(true.B)
          dut.clock.step(1)
          dut.io.spi.sclk.poke(false.B)
          dut.clock.step(1)
        }
        dut.io.apb.PRDATA.expect(0xaa.U)
      }
  }
}
 */
