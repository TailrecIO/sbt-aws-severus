package io.tailrec.sbt.severus.config

import java.io.File
import com.typesafe.config._
import scala.collection.JavaConverters._

/**
  * @author Hussachai Puripunpinyo
  */
object SeverusConfig {

  val FileName = "severus.conf"

  def parseString(file: String): SeverusConfig = create(ConfigFactory.parseString(file))

  def parseFile(file: File): SeverusConfig = create(ConfigFactory.parseFile(file))

  private def create(config: Config): SeverusConfig = {

    val namespace = "aws"
    val defaultConfig = ConfigFactory.parseResources(getClass.getClassLoader, FileName).getConfig(namespace)
    if(config.isEmpty){
      println("Configuration file is missing!")
      new SeverusConfig(defaultConfig)
    } else {
      new SeverusConfig(config.getConfig(namespace).withFallback(defaultConfig))
    }
  }

  implicit class ScalaConfig(config: Config) {

    def getAs[T](path: String)(implicit reader: ConfigReader[T]): T = reader.read(config, path)

    def getAsOpt[T](path: String)(implicit reader: ConfigReader[T]): Option[T] = {
      try {
        Some(getAs[T](path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }

    def getAsObjectList(path: String): List[ConfigObject] = config.getObjectList(path).asScala.toList

    def getAsConfigList(path: String): List[Config] = config.getConfigList(path).asScala.toList

  }

  trait ConfigReader[T] {
    def read(config: Config, path: String): T
  }

  implicit val StringConfigReader = new ConfigReader[String]{
    def read(config: Config, path: String): String = config.getString(path)
  }

  implicit val StringListConfigReader = new ConfigReader[List[String]]{
    def read(config: Config, path: String): List[String] = config.getStringList(path).asScala.toList
  }

  implicit val IntConfigReader = new ConfigReader[Int]{
    def read(config: Config, path: String): Int = config.getInt(path)
  }

  implicit val BooleanConfigReader = new ConfigReader[Boolean]{
    def read(config: Config, path: String): Boolean = config.getBoolean(path)
  }
}

class SeverusConfig(val config: Config) {

  val iam = new IamConfig(config.getConfig("iam"))

  val s3 = new S3Config(config.getConfig("s3"))

  val lambda = new LambdaConfig(config.getConfig("lambda"))

}
