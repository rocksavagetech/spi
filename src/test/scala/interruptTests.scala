package tech.rocksavage.chiselware.SPI

import scala.math.pow
import scala.util.Random

import TestUtils._
import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.matchers.should.Matchers._


object interruptTests extends ApbUtils {
    def txComplete(
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
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00000001".U) // Enable TXCIF interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until myParams.dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1  // Sending MSB first
          val slaveBit = (slaveData >> (myParams.dataWidth - 1 - i)) & 1    // Sending MSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)
          dut.clock.step(4)
        }
        dut.clock.step(2)
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt >> 7) should be (1) // Check if the transmission complete flag is set
    }

    def wcolFlag(
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
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode
        writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00000001".U) // Enable TXCIF interrupt

        writeAPB(dut.io.masterApb, dut.master.regs.DATA_ADDR.U, masterData.U) // Write new data before transfer completes
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt & (1 << 6)) >> 6  should be (1) // Check if the write collision flag is set
    }

    def dataEmpty(
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
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLB_ADDR.U, "b10000000".U) // Enable DREIF interrupt
        //writeAPB(dut.io.masterApb, dut.master.regs.INTCTRL_ADDR.U, "b00100000".U) // Enable DREIF interrupt
        writeAPB(dut.io.slaveApb, dut.slave.regs.CTRLA_ADDR.U, "b00000001".U) // Set Slave
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100001".U) // Set Master in Master mode

        // Extract the current bit from the master and slave data
        val masterBit = (masterData >> (myParams.dataWidth - 1)) & 1  // Sending MSB first
        val slaveBit = (slaveData >> (myParams.dataWidth - 1)) & 1    // Sending MSB first

        dut.clock.step(2)
        val readTxInt = readAPB(dut.io.masterApb, dut.master.regs.INTFLAGS_ADDR.U)
        (readTxInt & (1 << 5)) >> 5  should be (0) // Check if the data register empty flag is set. 0 is for empty now.
    }
}