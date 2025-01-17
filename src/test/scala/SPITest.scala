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
import chiseltest.simulator._
import firrtl2.AnnotationSeq
import firrtl2.annotations.Annotation // Correct Annotation type for firrtl2
import firrtl2.options.TargetDirAnnotation

/** Highly randomized test suite driven by configuration parameters. Includes
  * code coverage for all top-level ports. Inspired by the DynamicFifo
  */

class SpiTest
    extends AnyFlatSpec
    with ChiselScalatestTester
    with Matchers {

  val verbose = false
  val numTests = 2
  val testName = System.getProperty("testName")
  println(s"Argument passed: $testName")

  // System properties for flags
  val enableVcd = System.getProperty("enableVcd", "true").toBoolean
  val enableFst = System.getProperty("enableFst", "false").toBoolean
  val useVerilator = System.getProperty("useVerilator", "false").toBoolean

  val buildRoot = sys.env.get("BUILD_ROOT_RELATIVE")
  if (buildRoot.isEmpty) {
    println("BUILD_ROOT_RELATIVE not set, please set and run again")
    System.exit(1)
  }
  val testDir = buildRoot.get + "/test"

  println(
    s"Test: $testName, VCD: $enableVcd, FST: $enableFst, Verilator: $useVerilator"
  )

  // Constructing the backend annotations based on the flags
  val backendAnnotations = {
    var annos: Seq[Annotation] = Seq() // Initialize with correct type

    if (enableVcd) annos = annos :+ chiseltest.simulator.WriteVcdAnnotation
    if (enableFst) annos = annos :+ chiseltest.simulator.WriteFstAnnotation
    if (useVerilator) {
      annos = annos :+ chiseltest.simulator.VerilatorBackendAnnotation
      annos = annos :+ VerilatorCFlags(Seq("--std=c++17"))
    }
    annos = annos :+ TargetDirAnnotation(testDir)

    annos
  }

  // Execute the regressigiyon across a randomized range of configurations
  if (testName == "regression") (1 to numTests).foreach { config =>
    main(s"GPIO_test_config_$config")
  }
  else {
    main(testName)
  }

  def main(testName: String): Unit = {
    behavior of testName

    // Randomize Input Variables
    val validDataWidths = Seq(8, 16, 32)
    val validPAddrWidths = Seq(8, 16, 32)
    val dataWidth = 8 // validDataWidths(Random.nextInt(validDataWidths.length))
    val addrWidth = validPAddrWidths(Random.nextInt(validPAddrWidths.length))

    // Pass in randomly selected values to DUT
    val myParams = BaseParams(dataWidth, addrWidth, 8)

      info(s"Data Width = $dataWidth")
      info(s"Address Width = $addrWidth")
      info("--------------------------------")

    testName match {
        // Test case for Master Mode Initialization
        case "masterMode" =>
            it should "initialize the SPI core in Master Mode correctly" in {
            val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.masterMode(dut, myParams)
            }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test case for Slave Mode Initialization
        case "slaveMode" =>
            it should "initialize the SPI core in Slave Mode correctly" in {
                val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.slaveMode(dut, myParams)
            }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 2.1: Full Duplex Transmission (Master-Slave) for all SPI Modes with Randomized DataWidth
        case "fullDuplex" =>
            it should "transmit and receive data correctly in Full Duplex mode (Master-Slave) for all SPI modes" in {
            val cov =  test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.fullDuplex(dut, myParams)
            }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 2.2: MSB First and LSB First Data Order
        case "bitOrder" =>
            it should "transmit and receive data correctly in MSB and LSB first modes" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.bitOrder(dut, myParams)
            }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        case "transmitTests" =>
            transmitTestsFull(myParams)

        // 3.1 Clock Speed Tests
        case "prescaler" =>
            it should "clock speed test for prescalar 0x2(64 times slower)" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                clockTests.prescaler(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 3.2: Double-Speed Master SPI Mode
        case "doubleSpeed" =>
            it should "clock speed for clk2x with prescalar of 8 times slower" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                clockTests.doubleSpeed(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        case "clockTests" =>
            clockTestsFull(myParams)

        // Test 4.1: Transmission Complete Interrupt Flag
        case "txComplete" =>
            it should "transmission complete interrupt flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.txComplete(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 4.2: Write Collision Flag
        case "wcolFLag" =>
            it should "write collision flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.wcolFlag(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 4.3: Data Register Empty Interrupt
        case "dataEmpty" =>
            it should "data register empty interrupt flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.dataEmpty(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        case "overFlow" =>
            it should "cause buffer overflow flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.overFlow(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        case "interruptTests" =>
            interruptTestsFull(myParams)

        // Test 7.2: Buffered Mode Master
        // Enable Buffered Mode and check that multiple bytes can be written to the transmit buffer before the transfer completes.
        // Verify that received data is stored in the FIFO correctly.
        case "bufferTx" =>
            it should "buffered mode master" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.bufferTx(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 7.3: Recieve Register Check Normal Mode
        case "normalRx" =>
            it should "recieve register correct normal mode" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.normalRx(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        // Test 7.4: Recieve Register Check Buffer Mode Mode
        case "bufferRx" =>
            it should "recieve register correct buffer" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.bufferRx(dut, myParams)
                }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        //Test 8.1: Daisy Chain Test with 3 Slaves
        case "daisyChain" =>
            it should "daisy chain correctly" in {
            val cov = test(new DaisyChainSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.daisyChain(dut, myParams)
            }
            coverageCollection(cov.getAnnotationSeq, myParams, testName)
            }

        case "modeTests" =>
            modeTestsFull(myParams)

        case "allTests" =>
            allTests(myParams)

        case _ => allTests(myParams)
        }

      // Test 6.1: Master Deactivation upon SS Low
      // In a multi-master scenario, configure the SS pin to control master activation.
      // Drive SS low and ensure the SPI automatically switches from Master to Slave mode.

      // Test 6.2: Tri-state MISO in Slave Mode
      // In Slave mode, configure the MISO pin as output.
      // When SS is high, ensure MISO is tri-stated (disconnected).
      // When SS is low, verify that MISO outputs data correctly.

      // Test 7.3: Normal Mode Slave
      // In Slave mode, ensure the SPI logic halts when SS is high and resumes when SS is low.

      // Test 7.4: Buffered Mode Slave
      // Enable Buffered Mode in Slave mode and verify that multiple received bytes are stored in the FIFO and transmitted correctly.

        it should "generate cumulative coverage report" in {
        coverageCollector.saveCumulativeCoverage(myParams)
        }
    }



    //}

  def allTests(
      myParams: BaseParams
  ): Unit = {
    transmitTestsFull(myParams)
    clockTestsFull(myParams)
    interruptTestsFull(myParams)
    modeTestsFull(myParams)
  }

  def transmitTestsFull(
      myParams: BaseParams
  ): Unit = {

    it should "initialize the SPI core in Master Mode correctly" in {
    val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.masterMode(dut, myParams)
    }
    coverageCollection(cov.getAnnotationSeq, myParams, "masterMode")
    }

    it should "initialize the SPI core in Slave Mode correctly" in {
        val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.slaveMode(dut, myParams)
    }
    coverageCollection(cov.getAnnotationSeq, myParams, "slaveMode")
    }

    it should "transmit and receive data correctly in Full Duplex mode (Master-Slave) for all SPI modes" in {
    val cov =  test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.fullDuplex(dut, myParams)
    }
    coverageCollection(cov.getAnnotationSeq, myParams, "fullDuplex")
    }

    it should "transmit and receive data correctly in MSB and LSB first modes" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.bitOrder(dut, myParams)
    }
    coverageCollection(cov.getAnnotationSeq, myParams, "bitOrder")
    }

  }

  def clockTestsFull(
      myParams: BaseParams
  ): Unit = {

    it should "clock speed test for prescalar 0x2(64 times slower)" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        clockTests.prescaler(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "prescaler")
    }

    it should "clock speed for clk2x with prescalar of 8 times slower" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        clockTests.doubleSpeed(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "doubleSpeed")
    }
  }

  def interruptTestsFull(
      myParams: BaseParams
  ): Unit = {

    it should "transmission complete interrupt flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.txComplete(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "txComplete")
    }

    it should "write collision flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.wcolFlag(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "wcolFLag")
    }

    it should "data register empty interrupt flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.dataEmpty(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "dataEmpty")
    }

    it should "cause buffer overflow flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.overFlow(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "dataEmpty")
    }
  }

  def modeTestsFull(
      myParams: BaseParams
  ): Unit = {
    it should "buffered mode master" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.bufferTx(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "bufferTx")
    }

    it should "recieve register correct normal mode" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.normalRx(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "normalRx")
    }

    it should "recieve register correct buffer" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.bufferRx(dut, myParams)
        }
    coverageCollection(cov.getAnnotationSeq, myParams, "bufferRx")
    }

    it should "daisy chain correctly" in {
    val cov = test(new DaisyChainSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.daisyChain(dut, myParams)
    }
    coverageCollection(cov.getAnnotationSeq, myParams, "daisyChain")
    }
  }


  def coverageCollection(
    cov: Seq[Annotation],
    myParams: BaseParams,
    testName: String
    ): Unit = {
    if (myParams.coverage) {
      val coverage = cov
        .collectFirst { case a: TestCoverage => a.counts }
        .get
        .toMap

      val testConfig =
        myParams.addrWidth.toString + "_" + myParams.dataWidth.toString

      val buildRoot = sys.env.get("BUILD_ROOT")
      if (buildRoot.isEmpty) {
        println("BUILD_ROOT not set, please set and run again")
        System.exit(1)
      }
      // path join
      val scalaCoverageDir = new File(buildRoot.get + "/cov/scala")
      val verCoverageDir = new File(buildRoot.get + "/cov/verilog")
      verCoverageDir.mkdirs()
      val coverageFile = verCoverageDir.toString + "/" + testName + "_" +
        testConfig + ".cov"

      val stuckAtFault = checkCoverage(coverage, coverageFile)
      if (stuckAtFault)
        println(
          s"WARNING: At least one IO port did not toggle -- see $coverageFile"
        )
      info(s"Verilog Coverage report written to $coverageFile")
    }
  }
}


