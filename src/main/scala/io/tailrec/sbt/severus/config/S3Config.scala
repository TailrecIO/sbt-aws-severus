package io.tailrec.sbt.severus.config

import com.amazonaws.regions.{Region, Regions}
import com.typesafe.config.Config
import io.tailrec.sbt.severus.config.SeverusConfig._

/**
  * @author Hussachai Puripunpinyo
  */
class S3Config(config: Config) {

  val region: Region = Region.getRegion(Regions.fromName(config.getAs[String]("region")))

}
