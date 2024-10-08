package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

case class BaseParams(
    spiMaster: Boolean = true,
    spiMode: Int = 1, // Only valid for ranges 1 - 4
    clockFreq: Int = 2, //2 = 50 MHz
    dataWidth: Int = 8,
    addrWidth: Int = 8,
    regWidth: Int = 8
) {
  require(spiMode <= 4 && spiMode >= 1, "spiMode must be in range 1-4")
}
