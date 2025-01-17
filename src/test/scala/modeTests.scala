package tech.rocksavage.chiselware.SPI

import scala.math.pow
import scala.util.Random
import TestUtils._
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.Assertions._
import org.scalatest.flatspec.AnyFlatSpec
import tech.rocksavage.chiselware.SPI.ApbUtils.{readAPB, writeAPB}

object modeTests {
    def bufferTx(
        dut: FullDuplexSPI,
        myParams: BaseParams
    ): Unit = {
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val slaveData = BigInt(myParams.dataWidth, Random)   // Randomized data for slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode. Needs to be done BEFORE writing data
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode for slave too

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
        val readFlag2 = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        require(readFlag2 === 64)
        val readReg = readAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U)
        println(s"readReg Read: ${readReg.toString()}")
        println(s"masterData Read: ${masterData.toString()}")
        require(readReg === masterData)
        dut.clock.step(2*(myParams.dataWidth-1))
        val readReg2 = readAPB(dut.io.slaveApb, dut.slave.regs.DATA_ADDR.U)
        require(readReg2 === transmitBuffer)
        dut.clock.step(2*(myParams.dataWidth-1))

    }

    def normalRx(
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

        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00000001".U) //Enable transmit complete interrupt

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
        val readReg = readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U)
        val readInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        require(readReg === slaveData)
        require(readInt === 128)
        writeAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U, "b10000000".U)  //Clear Interrupt
        require(readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U) === 0)
    }

    def daisyChain(
        dut: DaisyChainSPI,
        myParams: BaseParams
    ): Unit = {   
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData1 = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val masterData2 = BigInt(myParams.dataWidth, Random)   // Randomized data
        val masterData3 = BigInt(myParams.dataWidth, Random) 
        val masterData4 = BigInt(myParams.dataWidth, Random) 
        //writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode. Needs to be done BEFORE writing data
        //writeAPB(dut.io.slave1Apb, dut.slave1.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode for slave too
        //writeAPB(dut.io.slave2Apb, dut.slave2.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode for slave too

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        writeAPB(dut.io.slave1Apb, dut.slave1.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.slave2Apb, dut.slave2.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        //writeAPB(dut.io.slave3Apb, dut.slave3.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData1.U)
        dut.clock.step(1)
        //writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData2.U) // Write new data before transfer completes
        // check that transmit buffer is not empty and has expected value
        //dut.clock.step(2*(myParams.dataWidth-1))

          for (i <- 0 until myParams.dataWidth) {
            dut.clock.step(4)
          }
          dut.clock.step(8)
          val readRegS1 = readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U)
          require(readRegS1 === masterData1)

          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData3.U)
          for (i <- 0 until myParams.dataWidth) {
              dut.clock.step(4)
          }
          dut.clock.step(8)
          val readReg1S1 = readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U)
          require(readReg1S1 === masterData3)
          val readRegS2 = readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U)
          require(readRegS2 === masterData1)          

          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData4.U)
          for (i <- 0 until myParams.dataWidth*3) {  //Need to go through 3 more cycles for everything to loop back to master
              dut.clock.step(4)
         }

          val readReg2S1 = readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U)
          require(readReg2S1 === masterData4)
          val readReg1S2 = readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U)
          require(readReg1S2 === masterData3)    
          val readRegM = readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U)
          require(readRegM === masterData1) 
    }

      def daisyChainBuffer(
        dut: DaisyChainSPI,
        myParams: BaseParams
    ): Unit = {   
        implicit val clk: Clock = dut.clock // Provide implicit clock

        // Generate random data for Master and Slave according to the randomized myParams.dataWidth
        val masterData1 = BigInt(myParams.dataWidth, Random)  // Randomized data for master
        val masterData2 = BigInt(myParams.dataWidth, Random)   // Randomized data
        val masterData3 = BigInt(myParams.dataWidth, Random) 
        val masterData4 = BigInt(myParams.dataWidth, Random) 
        val slaveData1 = BigInt(myParams.dataWidth, Random)
        val slaveData2 = BigInt(myParams.dataWidth, Random)

        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode. Needs to be done BEFORE writing data
        //writeAPB(dut.io.slave1Apb, dut.slave1.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode for slave too
        //writeAPB(dut.io.slave2Apb, dut.slave2.regs.CTRLB_ADDR.U, "b10000000".U) // Enable Buffer Mode for slave too

        // Enable both Master and Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        writeAPB(dut.io.slave1Apb, dut.slave1.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.slave2Apb, dut.slave2.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave

        // Set up Master to transmit and Slave to receive
        writeAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U, slaveData1.U)
        writeAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U, slaveData2.U)
   
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData1.U)
        dut.clock.step(1)
        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData2.U) // Write new data before transfer completes
        // check that transmit buffer is not empty and has expected value
        //dut.clock.step(2*(myParams.dataWidth-1))

          for (i <- 0 until myParams.dataWidth) {
            dut.clock.step(4)
          }
          //require(readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U) === 0.U)
          require(readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U) === masterData1)
          require(readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U) === slaveData1)
          for (i <- 0 until myParams.dataWidth) {
            dut.clock.step(4)
          }         
          dut.clock.step(8)
          require(readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U) === slaveData2)
          require(readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U) === masterData2)
          require(readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U) === masterData1)

          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData3.U)
          for (i <- 0 until myParams.dataWidth) {
              dut.clock.step(4)
          }
          dut.clock.step(8)
          require(readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U) === slaveData1)
          require(readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U) === masterData3)
          require(readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U) === masterData2)        

          writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData4.U)
          for (i <- 0 until myParams.dataWidth*3) {  //Need to go through 3 more cycles for everything to loop back to master
              dut.clock.step(4)
         }

          require(readAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U) === masterData1)
          require(readAPB(dut.io.slave1Apb, dut.slave1.regs.DATA_ADDR.U) === masterData4)
          require(readAPB(dut.io.slave2Apb, dut.slave2.regs.DATA_ADDR.U) === masterData3)        
    }
}