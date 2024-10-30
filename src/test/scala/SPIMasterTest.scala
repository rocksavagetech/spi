package tech.rocksavage.chiselware.SPI

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.{util => ju}

import scala.math.pow
import scala.util.Random

import org.scalatest.Assertions._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

//import tech.rocksavage.chiselware.util.TestUtils.{randData, checkCoverage}
import TestUtils.checkCoverage
import TestUtils.randData
import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.coverage._
import chiseltest.simulator.VerilatorCFlags
import firrtl2.options.TargetDirAnnotation

/** Highly randomized test suite driven by configuration parameters. Includes
  * code coverage for all top-level ports. Inspired by the DynamicFifo
  */

class GPIOTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers
    with APBUtils {

  val verbose = false
  val numTests = 1

  def main(testName: String): Unit = {
    behavior of testName

    val backendAnnotations = Seq(
      // WriteVcdAnnotation,
      // WriteFstAnnotation,
      // VerilatorBackendAnnotation, // For using verilator simulator
      // IcarusBackendAnnotation,
      // VcsBackendAnnotation,
      TargetDirAnnotation("generated")
    )

    // val myParams =
    //   BaseParams(true, 1, 2, 8, 8)

    // testName should "pass" in {
    //   val cov = test(new SPIMaster(myParams))
    //     .withAnnotations(backendAnnotations) { dut =>
    //       // Reset Sequence
    //       dut.reset.poke(true.B)
    //       dut.clock.step()
    //       dut.reset.poke(false.B)

    //       writeAPB(dut, 0.U, 25.U)
    //       for (i <- 0 until 25) {
    //         dut.clock.step()
    //         dut.io.pins.miso.poke(1)
    //         // println(s"MOSI: ${dut.io.pins.mosi.peek().litValue}")
    //         // dut.io.pins.mosi.expect(0xa5.U & (1 << i).U)
    //       }
    //       println(s"MISO: ${readAPB(dut, 0.U)}")
    //     }
    // }
  }

  (1 to numTests).foreach(config => main(s"test$config"))

}
