package tech.rocksavage.chiselware.SPI

import tech.rocksavage.chiselware.SPI.BaseParams
import tech.rocksavage.traits.ModuleConfig

class SpiConfig extends ModuleConfig {
    override def getDefaultConfigs: Map[String, Any] = Map(
      "8_8" -> Seq(
        BaseParams(
            dataWidth = 8,
            addrWidth = 8,
            regWidth = 8
        )
      ),
      "16_16" -> Seq(
        BaseParams(
            dataWidth = 16,
            addrWidth = 16,
            regWidth = 8
        )
      ),
      "32_32" -> Seq(
        BaseParams(
            dataWidth = 32,
            addrWidth = 32,
            regWidth = 8
        )
      ),
    )
}