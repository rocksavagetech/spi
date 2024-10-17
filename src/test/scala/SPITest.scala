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

class SPITest
    extends AnyFlatSpec with ChiselScalatestTester with Matchers with APBUtils {

  val verbose = false
  val numTests = 1

  // System properties for flags
  val enableVcd = System.getProperty("enableVcd", "false").toBoolean
  val enableFst = System.getProperty("enableFst", "false").toBoolean
  val useVerilator = System.getProperty("useVerilator", "true").toBoolean

  val buildRoot = sys.env.get("BUILD_ROOT_RELATIVE")
  if (buildRoot.isEmpty) {
    println("BUILD_ROOT_RELATIVE not set, please set and run again")
    System.exit(1)
  }
  val testDir = buildRoot.get + "/test"

  println(s"VCD: $enableVcd, FST: $enableFst, Verilator: $useVerilator")

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

  // Add regression across a randomized range of configurations
  main()

  def main(): Unit = {
    behavior of "spi module"

    // Randomize Input Variables
    val validDataWidths = Seq(8, 16, 32)
    val validPAddrWidths = Seq(8, 16, 32)
    val dataWidth = 8 //validDataWidths(Random.nextInt(validDataWidths.length))
    val addrWidth = validPAddrWidths(Random.nextInt(validPAddrWidths.length))

    // Pass in randomly selected values to DUT
    val myParams = BaseParams(dataWidth, addrWidth, 8)

    // Test case for Master Mode Initialization
    it should "initialize the SPI core in Master Mode correctly" in {
      test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock
        // Set SPI to Master Mode by writing to CTRLA register
        writeAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U, "b00100001".U) // Set MASTER bit, Set ENABLE bit

        readAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U) should be (33) // Check CTRLA is set correctly
        dut.io.master.cs.expect(true.B) // Assert CS line (Active low)
      }
    }

    // // Test case for Slave Mode Initialization
    it should "initialize the SPI core in Slave Mode correctly" in {
      test(new SPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock
        // Set SPI to Slave Mode by writing to CTRLA register
        writeAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U, "b00000001".U) // Set ENABLE bit

        readAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U) should be (1) // Check CTRLA is set correctly for Slave mode
        dut.io.slave.cs.expect(false.B) // initial conditions of SS line
        dut.io.slave.miso.expect(false.B) // Check if MISO is controlled properly in slave mode
        dut.io.master.mosi.expect(false.B) // Check if MOSI is controlled properly in slave mode
      }
    }

    // Test 2.1: Full Duplex Transmission (Master-Slave) for all SPI Modes with Randomized DataWidth
    it should "transmit and receive data correctly in Full Duplex mode (Master-Slave) for all SPI modes" in {
      // Loop through all 4 SPI modes
      for (mode <- 0 until 4) {
        test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
          implicit val clk: Clock = dut.clock // Provide implicit clock

          // Generate random data for Master and Slave according to the randomized dataWidth
          val masterData = BigInt(dataWidth, Random)  // Randomized data for master
          val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

          // Configure SPI mode by setting bits 1:0 of the CTRLB register
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, mode.U) // Set SPI mode in Master
          writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLB_ADDR.U, mode.U)   // Set SPI mode in Slave

          // Set up Master to transmit and Slave to receive
          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
          writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

          // Enable both Master and Slave
          writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode

          // Determine CPOL and CPHA based on mode
          val (cpol, cpha) = (mode >> 1, mode & 1) // CPOL is bit 1, CPHA is bit 0

          // Simulate SPI clock cycles
          for (i <- 0 until dataWidth) {
            // Extract the current bit from the master and slave data
            val masterBit = (masterData >> (dataWidth - 1 - i)) & 1  // Sending MSB first
            val slaveBit = (slaveData >> (dataWidth - 1 - i)) & 1    // Sending MSB first

            // Perform the clocking based on CPHA
            if (cpha == 0) {
              // CPHA = 0: Sample on leading edge, shift on trailing edge
              dut.io.master.mosi.expect(masterBit.B)
              dut.io.slave.miso.expect(slaveBit.B)
              dut.clock.step(1) // Leading edge (rising or falling depending on CPOL)
              dut.clock.step(1)
            } else {
              // Insert a step here to ensure data is stable before checking the bit.
              dut.clock.step(1)  // Add this clock step to synchronize correctly
              // Now check the sampled values (trailing edge)
              dut.io.master.mosi.expect(masterBit.B)
              dut.io.slave.miso.expect(slaveBit.B)
              dut.clock.step(1)

            }
          }
        }
      }
    }

    // Test 2.2: MSB First and LSB First Data Order
    it should "transmit and receive data correctly in MSB and LSB first modes" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b01000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b01100001".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = ((masterData & (1 << i)) >> i) & 1 // sending LSB first
          val slaveBit = ((slaveData & (1 << i)) >> i) & 1 // sending LSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)

          dut.clock.step(2)
        }
      } 
    }

    // 3.1 Clock Speed Tests
    it should "clock speed test for prescalar 2(16 times slower)" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100101".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = (masterData >> (dataWidth - 1 - i)) & 1  // Sending MSB first
          val slaveBit = (slaveData >> (dataWidth - 1 - i)) & 1    // Sending MSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)
          dut.clock.step(32)
        }
      } 
    }

    // Test 3.2: Double-Speed Master SPI Mode
    it should " clock speed for clk2x with prescalar of 4 times slower" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00110011".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = (masterData >> (dataWidth - 1 - i)) & 1  // Sending MSB first
          val slaveBit = (slaveData >> (dataWidth - 1 - i)) & 1    // Sending MSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)
          dut.clock.step(4)
        }
      } 
    }

    // Test 4.1: Transmission Complete Interrupt Flag
    it should "transmission complete interrupt flag" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00000001".U) // Enable TXCIF interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        
        // Simulate SPI clock cycles
        for (i <- 0 until dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = (masterData >> (dataWidth - 1 - i)) & 1  // Sending MSB first
          val slaveBit = (slaveData >> (dataWidth - 1 - i)) & 1    // Sending MSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)
          dut.clock.step(2)
        }
        dut.clock.step(2)
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt >> 7)  should be (1) // Check if the transmission complete flag is set
      } 
    }

    // Test 4.2: Write Collision Flag
    it should "write collision flag" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00000001".U) // Enable TXCIF interrupt

        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U) // Write new data before transfer completes
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt & (1 << 6)) >> 6  should be (1) // Check if the write collision flag is set
      }
    }

    // Test 4.3: Data Register Empty Interrupt
    it should "data register empty interrupt flag" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable DREIF interrupt
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00100000".U) // Enable DREIF interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode

        // Extract the current bit from the master and slave data
        val masterBit = (masterData >> (dataWidth - 1)) & 1  // Sending MSB first
        val slaveBit = (slaveData >> (dataWidth - 1)) & 1    // Sending MSB first

        dut.clock.step(2)   
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt & (1 << 5)) >> 5  should be (1) // Check if the data register empty flag is set
      }
    }

    // Test 5.1: Lost Data Due to Buffer Overflow
    // In Buffer mode, ensure data is not lost if the receive FIFO is not read in time.
    // Check that the appropriate error flag is set when data is lost.
    it should "lost data due to buffer overflow" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable DREIF interrupt
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00100000".U) // Enable DREIF interrupt
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave

        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U) // Write new data before transfer completes
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U) // Write new data before transfer completes
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt & (1))  should be (1) // Check if the write collision flag is set
      }
    }

    // Test 7.2: Buffered Mode Master
    // Enable Buffered Mode and check that multiple bytes can be written to the transmit buffer before the transfer completes.
    // Verify that received data is stored in the FIFO correctly.
    it should "buffered mode master" in {
      test(new FullDuplexSPI(myParams)).withAnnotations(backendAnnotations) { dut =>
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized dataWidth
        val masterData = BigInt(dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable DREIF interrupt
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00100000".U) // Enable DREIF interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode

        val transmitBuffer = BigInt(dataWidth, Random)  // Randomized data for master
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, transmitBuffer.U) // Write new data before transfer completes
        // check that transmit buffer is not empty and has expected value
        dut.clock.step(2*(dataWidth-1))

        for (i <- 0 until dataWidth) {
          val masterBit = (transmitBuffer >> (dataWidth - 1 - i)) & 1  // Sending MSB first
          dut.io.master.mosi.expect(masterBit.B)
          dut.clock.step(2)
        }
      }
    }
  }
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

// Test 8.1: Add daisy Chaining module and test that with just one test