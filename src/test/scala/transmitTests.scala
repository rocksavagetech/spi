package tech.rocksavage.chiselware.SPI

import scala.math.pow
import scala.util.Random
//import TestUtils._
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.matchers.should.Matchers._
import tech.rocksavage.chiselware.SPI.ApbUtils.{readAPB, writeAPB}

object transmitTests {
  def masterMode(
      dut: SPI,
      myParams: BaseParams
  ): Unit = {
        implicit val clk: Clock = dut.clock // Provide implicit clock
        // Set SPI to Master Mode by writing to CTRLA register
        writeAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U, "b00100001".U) // Set MASTER bit, Set ENABLE bit

        readAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U) should be (33) // Check CTRLA is set correctly
        dut.io.master.cs.expect(true.B) // Assert CS line (Active low)
    }

    def slaveMode(
      dut: SPI,
      myParams: BaseParams
    ): Unit = {
        implicit val clk: Clock = dut.clock // Provide implicit clock
        // Set SPI to Slave Mode by writing to CTRLA register
        writeAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U, "b00000001".U) // Set ENABLE bit

        readAPB(dut.io.apb, dut.regs.CTRLA_ADDR.U) should be (1) // Check CTRLA is set correctly for Slave mode
        dut.io.slave.cs.expect(false.B) // initial conditions of SS line
        dut.io.slave.miso.expect(false.B) // Check if MISO is controlled properly in slave mode
        dut.io.master.mosi.expect(false.B) // Check if MOSI is controlled properly in slave mode
    }

    def fullDuplex(
        dut: FullDuplexSPI,
        myParams: BaseParams
    ): Unit = {
            // Define myParams.dataWidth as per your module's specification or randomize if needed
      // Iterate over all 4 SPI modes (0 to 3)
      for (mode <- 1 until 2) {
          implicit val clk: Clock = dut.clock // Provide implicit clock

          // Generate random data for Master and Slave
          val masterData = BigInt(myParams.dataWidth, Random)
          val slaveData = BigInt(myParams.dataWidth, Random)

          // Configure SPI mode by setting bits 1:0 of the CTRLB register
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, mode.U) // Set SPI mode in Master
          writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLB_ADDR.U, mode.U)   // Set SPI mode in Slave

          // Set up Master to transmit and Slave to receive
          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
          writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)
          
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100000".U) // Enable Master in Master mode

          //Test SCLK Leading Edge (Mode 0, 1: SCLK Low Default)
          //(Mode 2, 3: SCLK High Default)
          if(mode == 0 || mode == 1) {
            dut.io.master.sclk.expect(0);
            dut.io.slave.sclk.expect(0);
          } else{
            dut.io.master.sclk.expect(1);
            dut.io.slave.sclk.expect(1);           
          }

          // Enable both Master and Slave
          writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U)  // Enable Slave, Enable SPI
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Enable Master, Enable SPI 

          // Determine CPOL and CPHA based on mode
          val (cpol, cpha) = ((mode >> 1) & 1, mode & 1)

          // Fork Slave Reception Thread
          val slaveThread = fork {
            for (i <- 0 until myParams.dataWidth) {
              if (mode == 1 || mode == 2) {
                if(i==0) {  //Sample on falling edge
                  dut.clock.step(2)
                }
              }
              // Extract the expected bit from the slave data
              val expectedBit = (slaveData >> (myParams.dataWidth - 1 - i)) & 1
              dut.io.slave.miso.expect(expectedBit) 
              dut.clock.step(4)
            }
          }

          // Fork Master Transmission Thread
          val masterThread = fork {
            for (i <- 0 until myParams.dataWidth) {
              if (mode == 1 || mode == 2) {
                if(i==0) {  //Sample on falling edge
                  dut.clock.step(2)
                }
              }
              // Extract the current bit from the master data (MSB first)
              val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1
              dut.io.master.mosi.expect(masterBit.B)
              dut.clock.step(4)
            }
          }

          // Join Both Threads to ensure completion
          masterThread.join()
          slaveThread.join()
          dut.clock.step(8)
          // Assertions to verify data transmission
          val receivedMasterData = readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U)
          val receivedSlaveData = readAPB(dut.io.slaveApb, dut.master.regs.DATA_ADDR.U)

          // Verify that the master received the slave's data
          receivedMasterData should be (slaveData)

          // Verify that the slave received the master's data
          receivedSlaveData should be (masterData)
          writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000000".U)  // Disable SPI
          writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00000000".U) // Disable SPI
        }
    }

    def bitOrder(
        dut: FullDuplexSPI,
        myParams: BaseParams
    ): Unit = { 
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(myParams.dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b01000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b01100001".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until myParams.dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = ((masterData & (1 << i)) >> i) & 1 // sending LSB first
          val slaveBit = ((slaveData & (1 << i)) >> i) & 1 // sending LSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)

          dut.clock.step(4)
        }
    }

    def dataOrder(
        dut: FullDuplexSPI,
        myParams: BaseParams
    ): Unit = {
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(myParams.dataWidth, Random)   // Randomized data for slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b00000100".U) 
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100000".U) // Enable Master in Master mode
        dut.clock.step(128)
        // Enable both Master and Slave
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode, DIV64

        // Simulate SPI clock cycles
        for (i <- 0 until myParams.dataWidth) {
            // Extract the current bit from the master and slave data
            val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1  // Sending MSB first
            val slaveBit = (slaveData >> (myParams.dataWidth - 1 - i)) & 1    // Sending MSB first

            dut.clock.step(8)
        }
    }

    def dataOrderBuffer(
        dut: FullDuplexSPI,
        myParams: BaseParams
    ): Unit = {
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(myParams.dataWidth, Random)   // Randomized data for slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10010100".U) // Enable Buffer Mode. Needs to be done BEFORE writing data
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLB_ADDR.U, "b10010100".U) // Enable Buffer Mode for slave too

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U)
        writeAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U, slaveData.U)

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b01000000".U) // Enable Tx Comp interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        dut.clock.step(1)
        val transmitBuffer = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, transmitBuffer.U) // Write new data before transfer completes
        val readFlag = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        require(readFlag === 32)
        // check that transmit buffer is not empty and has expected value
        dut.clock.step(2*(myParams.dataWidth-1))

        for (i <- 0 until myParams.dataWidth*2) {
          if (i < myParams.dataWidth) {
            val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1  // Sending MSB first
            //dut.io.master.mosi.expect(masterBit.B)
          } else {
            val masterBit = (transmitBuffer >> (myParams.dataWidth - 1 - i)) & 1
            //dut.io.master.mosi.expect(masterBit.B)
          }
          dut.clock.step(4)
        }
    }

}