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
//import TestUtils.checkCoverage
//import TestUtils.randData
import chisel3._
import chisel3.util._
import chiseltest._
import chiseltest.coverage._
import chiseltest.simulator._
import firrtl2.AnnotationSeq
import firrtl2.annotations.Annotation // Correct Annotation type for firrtl2
import firrtl2.options.TargetDirAnnotation
import tech.rocksavage.test._

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
    val covDir   = "./out/cov"
    val coverage = true
    // Randomize Input Variables
    val validDataWidths = Seq(8, 16, 32)
    val validPAddrWidths = Seq(8, 16, 32)
    val dataWidth = 8 // validDataWidths(Random.nextInt(validDataWidths.length))
    val addrWidth = validPAddrWidths(Random.nextInt(validPAddrWidths.length))

    // Pass in randomly selected values to DUT
    val myParams = BaseParams(dataWidth, addrWidth, 8, true)
    val myParamsDaisy = BaseParams(dataWidth, addrWidth, 8, false)
    val configName = dataWidth + "_" + addrWidth + "_8"


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
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test case for Slave Mode Initialization
        case "slaveMode" =>
            it should "initialize the SPI core in Slave Mode correctly" in {
                val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.slaveMode(dut, myParams)
            }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 2.1: Full Duplex Transmission (Master-Slave) for all SPI Modes with Randomized DataWidth
        case "fullDuplex" =>
            it should "transmit and receive data correctly in Full Duplex mode (Master-Slave) for all SPI modes" in {
            val cov =  test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.fullDuplex(dut, myParams)
            }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 2.2: MSB First and LSB First Data Order
        case "bitOrder" =>
            it should "transmit and receive data correctly in MSB and LSB first modes" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                transmitTests.bitOrder(dut, myParams)
            }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        case "transmitTests" =>
            transmitTestsFull(myParams, configName, covDir, coverage)

        // 3.1 Clock Speed Tests
        case "prescaler" =>
            it should "clock speed test for prescalar 0x2(64 times slower)" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                clockTests.prescaler(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 3.2: Double-Speed Master SPI Mode
        case "doubleSpeed" =>
            it should "clock speed for clk2x with prescalar of 8 times slower" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                clockTests.doubleSpeed(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        case "clockTests" =>
            clockTestsFull(myParams, configName, covDir, coverage)

        // Test 4.1: Transmission Complete Interrupt Flag
        case "txComplete" =>
            it should "transmission complete interrupt flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.txComplete(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 4.2: Write Collision Flag
        case "wcolFLag" =>
            it should "write collision flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.wcolFlag(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 4.3: Data Register Empty Interrupt
        case "dataEmpty" =>
            it should "data register empty interrupt flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.dataEmpty(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        case "overFlow" =>
            it should "cause buffer overflow flag" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                interruptTests.overFlow(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        case "interruptTests" =>
            interruptTestsFull(myParams, configName, covDir, coverage)

        // Test 7.2: Buffered Mode Master
        // Enable Buffered Mode and check that multiple bytes can be written to the transmit buffer before the transfer completes.
        // Verify that received data is stored in the FIFO correctly.
        case "bufferTx" =>
            it should "buffered mode master" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.bufferTx(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        // Test 7.3: Recieve Register Check Normal Mode
        case "normalRx" =>
            it should "recieve register correct normal mode" in {
            val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
                modeTests.normalRx(dut, myParams)
                }
            coverageCollector.collectCoverage(
              cov.getAnnotationSeq,
              testName,
              configName,
              coverage,
              covDir
            )
            }

        //Test 8.1: Daisy Chain Test with 3 Slaves
        case "daisyChain" =>
            it should "daisy chain correctly" in {
            val cov = test(new DaisyChainSPI(myParamsDaisy)).withAnnotations(backendAnnotations) { dut =>
                modeTests.daisyChain(dut, myParamsDaisy)
            }
            coverageCollector.collectCoverage(
                cov.getAnnotationSeq,
                testName,
                configName,
                false,
                covDir
            )
            }

        case "daisyChainBuffer" =>
            it should "daisy chain + buffer correctly" in {
            val cov = test(new DaisyChainSPI(myParamsDaisy)).withAnnotations(backendAnnotations) { dut =>
                modeTests.daisyChainBuffer(dut, myParamsDaisy)
            }
            coverageCollector.collectCoverage(
                cov.getAnnotationSeq,
                testName,
                configName,
                false,
                covDir
            )
            }

        case "modeTests" =>
            modeTestsFull(myParams, myParamsDaisy, configName, covDir, coverage)

        case "allTests" =>
            allTests(myParams, myParamsDaisy, configName, covDir, coverage)

        case _ => allTests(myParams, myParamsDaisy, configName, covDir, coverage)
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
        coverageCollector.saveCumulativeCoverage(coverage, covDir)
        }
    }



    //}

  def allTests(
      myParams: BaseParams,
      myParamsDaisy: BaseParams,
      configName: String,
      covDir: String,
      coverage: Boolean
  ): Unit = {
    transmitTestsFull(myParams, configName, covDir, coverage)
    clockTestsFull(myParams, configName, covDir, coverage)
    interruptTestsFull(myParams, configName, covDir, coverage)
    modeTestsFull(myParams, myParamsDaisy, configName, covDir, coverage)
  }

  def transmitTestsFull(
    myParams: BaseParams,
    configName: String,
    covDir: String,
    coverage: Boolean
  ): Unit = {

    it should "initialize the SPI core in Master Mode correctly" in {
    val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.masterMode(dut, myParams)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "masterMode",
        configName,
        coverage,
        covDir
      )
    }

    it should "initialize the SPI core in Slave Mode correctly" in {
        val cov = test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.slaveMode(dut, myParams)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "slaveMode",
        configName,
        coverage,
        covDir
      )
    }

    it should "transmit and receive data correctly in Full Duplex mode (Master-Slave) for all SPI modes" in {
    val cov =  test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.fullDuplex(dut, myParams)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "fullDuplex",
        configName,
        coverage,
        covDir
      )
    }

    it should "transmit and receive data correctly in MSB and LSB first modes" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        transmitTests.bitOrder(dut, myParams)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "bitOrder",
        configName,
        coverage,
        covDir
      )
    }

  }

  def clockTestsFull(
    myParams: BaseParams,
    configName: String,
    covDir: String,
    coverage: Boolean
  ): Unit = {

    it should "clock speed test for prescalar 0x2(64 times slower)" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        clockTests.prescaler(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "prescaler",
        configName,
        coverage,
        covDir
      )
    }

    it should "clock speed for clk2x with prescalar of 8 times slower" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        clockTests.doubleSpeed(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "doubleSpeed",
        configName,
        coverage,
        covDir
      )
    }
  }

  def interruptTestsFull(
    myParams: BaseParams,
    configName: String,
    covDir: String,
    coverage: Boolean
  ): Unit = {

    it should "transmission complete interrupt flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.txComplete(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "txComplete",
        configName,
        coverage,
        covDir
      )
    }

    it should "write collision flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.wcolFlag(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "wcolFlag",
        configName,
        coverage,
        covDir
      )
    }

    it should "data register empty interrupt flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.dataEmpty(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "dataEmpty",
        configName,
        coverage,
        covDir
      )
    }

    it should "cause buffer overflow flag" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        interruptTests.overFlow(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "overFlow",
        configName,
        coverage,
        covDir
      )
    }
  }

  def modeTestsFull(
      myParams: BaseParams,
      myParamsDaisy: BaseParams,
      configName: String,
      covDir: String,
      coverage: Boolean
  ): Unit = {
    it should "buffered mode master" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.bufferTx(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "bufferTx",
        configName,
        coverage,
        covDir
      )
    }

    it should "recieve register correct normal mode" in {
    val cov = test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        modeTests.normalRx(dut, myParams)
        }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "normalRx",
        configName,
        coverage,
        covDir
      )
    }


    it should "daisy chain correctly" in {
    val cov = test(new DaisyChainSPI(myParamsDaisy)).withAnnotations(backendAnnotations) { dut =>
        modeTests.daisyChain(dut, myParamsDaisy)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "daisyChain",
        configName,
        coverage,
        covDir
      )
    }

    it should "daisy chain + buffer correctly" in {
    val cov = test(new DaisyChainSPI(myParamsDaisy)).withAnnotations(backendAnnotations) { dut =>
        modeTests.daisyChainBuffer(dut, myParamsDaisy)
    }
    coverageCollector.collectCoverage(
        cov.getAnnotationSeq,
        "daisyChainBuffer",
        configName,
        coverage,
        covDir
      )
    }
  }
}


