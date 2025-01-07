package tech.rocksavage.chiselware.SPI

import scala.math.pow
import scala.util.Random

import TestUtils._
import chisel3._
import chisel3.util._
import chiseltest._

object clockTests extends ApbUtils {
    def prescaler(
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
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00100101".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until myParams.dataWidth) {
            // Extract the current bit from the master and slave data
            val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1  // Sending MSB first
            val slaveBit = (slaveData >> (myParams.dataWidth - 1 - i)) & 1    // Sending MSB first

            dut.io.master.mosi.expect(masterBit.B)
            dut.io.slave.miso.expect(slaveBit.B)
            dut.clock.step(64)
        }
    }

    def doubleSpeed(
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
        writeAPB(dut.io.masterApb, dut.master.regs.CTRLA_ADDR.U, "b00110011".U) // Set Master in Master mode

        // Simulate SPI clock cycles
        for (i <- 0 until myParams.dataWidth) {
          // Extract the current bit from the master and slave data
          val masterBit = (masterData >> (myParams.dataWidth - 1 - i)) & 1  // Sending MSB first
          val slaveBit = (slaveData >> (myParams.dataWidth - 1 - i)) & 1    // Sending MSB first

          dut.io.master.mosi.expect(masterBit.B)
          dut.io.slave.miso.expect(slaveBit.B)
          dut.clock.step(8)
        }
    }

}