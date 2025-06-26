package tech.rocksavage.chiselware.SPI

import chisel3._
import chisel3.util._

/** SPI Register Bundle.
  *
  * This bundle defines the control and status registers used in the SPI module,
  * along with their corresponding addresses and sizes.
  *
  * @param p Configuration parameters for the SPI (register width, data width, etc.).
  */
class SPIRegs(p: BaseParams) extends Bundle {
  /** Width of each register (default is 8 bits). */
  val REG_SIZE: Int = p.regWidth
  val CTRLA_SIZE: Int = p.regWidth
  val CTRLB_SIZE: Int = p.regWidth
  val INTCTRL_SIZE: Int = p.regWidth
  val INTFLAGS_SIZE: Int = p.regWidth
  val DATA_SIZE: Int = p.dataWidth

  /** Register Definitions */
  val CTRLA = RegInit(0.U(CTRLA_SIZE.W))
  val CTRLB = RegInit(0.U(CTRLB_SIZE.W))
  val INTCTRL = RegInit(0.U(INTCTRL_SIZE.W))
  val INTFLAGS = RegInit(0.U(INTFLAGS_SIZE.W))
  /** val DATA = RegInit(0.U(DATA_SIZE.W)) Not a physical register */

  /** Register Address and Size Definitions */

  /** Starting address of the CTRLA register. */
  val CTRLA_ADDR: Int = 0
  /** Number of REG_SIZE-wide words required for the CTRLA register. */
  val CTRLA_REG_SIZE: Int = (CTRLA_SIZE + REG_SIZE - 1) / REG_SIZE
  /** Maximum address of the CTRLA register. */
  val CTRLA_ADDR_MAX: Int = CTRLA_ADDR + CTRLA_REG_SIZE - 1

  val CTRLB_ADDR: Int = CTRLA_ADDR_MAX + 1
  val CTRLB_REG_SIZE: Int = (CTRLB_SIZE + REG_SIZE - 1) / REG_SIZE
  val CTRLB_ADDR_MAX: Int = CTRLB_ADDR + CTRLB_REG_SIZE - 1

  val INTCTRL_ADDR: Int = CTRLB_ADDR_MAX + 1
  val INTCTRL_REG_SIZE: Int = (INTCTRL_SIZE + REG_SIZE - 1) / REG_SIZE
  val INTCTRL_ADDR_MAX: Int = INTCTRL_ADDR + INTCTRL_REG_SIZE - 1

  val INTFLAGS_ADDR: Int = INTCTRL_ADDR_MAX + 1
  val INTFLAGS_REG_SIZE: Int = (INTFLAGS_SIZE + REG_SIZE - 1) / REG_SIZE
  val INTFLAGS_ADDR_MAX: Int = INTFLAGS_ADDR + INTFLAGS_REG_SIZE - 1

  /**
  val DATA_ADDR: Int = INTFLAGS_ADDR_MAX + 1
  val DATA_REG_SIZE: Int = (DATA_SIZE + REG_SIZE - 1) / REG_SIZE
  val DATA_ADDR_MAX: Int = DATA_ADDR + DATA_REG_SIZE - 1
  */
}
