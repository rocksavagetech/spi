// (c) 2024 Rocksavage Technology, Inc.
// This code is licensed under the Apache Software License 2.0 (see LICENSE.MD)

package tech.rocksavage.chiselware.GPIO

import chisel3._
import chisel3.util._

/** Default parameter settings for Dynamic FIFO
  *
  * @constructor
  *   default parameter settings
  * @param dataWidth
  *
  * specifies the width of the FIFO data
  * @param fifoDepth
  *   specifices the depth of the FIFO
  * @param externalRam
  *   specifies whether to use an external SRAM or generate internal FFs
  * @param coverage
  *   specifies whether to calculate port coverage during simulation
  * @author
  *   Warren Savage
  * @version 1.0
  *
  * @see
  *   [[http://www.rocksavage.tech]] for more information
  */
case class BaseParams(
    wordWidth: Int = 8,
    dataWidth: Int = 32,
    PDATA_WIDTH: Int = 32,
    PADDR_WIDTH: Int = 32,
    numVirtualPorts: Int = 8,
    sizeOfVirtualPorts: Int = log2Ceil(32),
    coverage: Boolean = false
)
