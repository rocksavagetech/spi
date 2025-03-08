package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

case class BaseParams(
    dataWidth: Int = 8,
    addrWidth: Int = 8,
    regWidth: Int = 8,
    coverage: Boolean = false
) {
  require(regWidth == 8, "regWidth must be 8")
  require(
    dataWidth == 8 || dataWidth == 16 || dataWidth == 32,
    "PDATA_WIDTH must be 8, 16, or 32"
  )
  require(addrWidth <= 32, "PADDR_WIDTH must be less than or equal to 32")
}
