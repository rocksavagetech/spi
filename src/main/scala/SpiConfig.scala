package tech.rocksavage.chiselware.SPI

import tech.rocksavage.chiselware.SPI.BaseParams
import tech.rocksavage.traits.ModuleConfig

/** SPI Configuration Class.
  *
  * Provides default configurations for SPI modules with varying data width
  * and address width combinations. These configurations are used to
  * initialize SPI modules with specific parameter sets.
  */
class SpiConfig extends ModuleConfig {
  /** Retrieves the default configurations for SPI modules.
    *
    * This method defines parameter sets for three combinations of data width
    * and address width:
    * - 8-bit data width and 8-bit address width
    * - 16-bit data width and 16-bit address width
    * - 32-bit data width and 32-bit address width
    *
    * @return A map containing the parameter sets keyed by a descriptive string.
    */
    override def getDefaultConfigs: Map[String, Any] = Map(
      "8_8" -> Seq(
        BaseParams(
            dataWidth = 8,
            addrWidth = 8,
            regWidth = 8
        )
      ),
      /** Configuration for 16-bit data width and 16-bit address width. */
      "16_16" -> Seq(
        BaseParams(
            dataWidth = 16,
            addrWidth = 16,
            regWidth = 8
        )
      ),
      /** Configuration for 32-bit data width and 32-bit address width. */
      "32_32" -> Seq(
        BaseParams(
            dataWidth = 32,
            addrWidth = 32,
            regWidth = 8
        )
      ),
    )
}