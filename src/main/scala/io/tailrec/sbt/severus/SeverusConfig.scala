package io.tailrec.sbt.severus

import java.io.File

import com.amazonaws.regions.{Region, RegionUtils}
import com.typesafe.config.{Config, ConfigException, ConfigFactory}
import io.tailrec.sbt.severus.SeverusConfig._
import io.tailrec.sbt.severus.SeverusModels._
import scala.collection.JavaConverters._

object SeverusConfig {

  def parseString(file: String) = create(ConfigFactory.parseString(file))

  def parseFile(file: File) = create(ConfigFactory.parseFile(file))

  private def create(config: Config) = {
    val defaultConfig = ConfigFactory.parseResources("severus.conf")
    new SeverusConfig(config.withFallback(defaultConfig).getConfig("aws.fun"))
  }

  implicit class ScalaConfig(config: Config) {

    def get[T](path: String)(implicit reader: ConfigReader[T]): T = reader.read(config, path)

    def getOpt[T](path: String)(implicit reader: ConfigReader[T]): Option[T] = {
      try {
        Some(get[T](path))
      } catch {
        case e: ConfigException.Missing => None
      }
    }
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

  object defaults {
    private val p = "defaults"
    val region: Region = RegionUtils.getRegion(config.get[String](s"$p.regions"))
    val role: String = config.get[String](s"$p.role")
    val memory: Int = config.get[Int](s"$p.memory")
    val timeout: Int = config.get[Int](s"$p.timeout")

    object artifact {
      private val p = s"defaults.${defaults.p}"
      val minified: Boolean = config.getOpt[Boolean](s"$p.minified").getOrElse(false)
      val s3Bucket: String = config.get[String](s"$p.s3Bucket")
    }
  }

  val artifacts: List[(String, ArtifactModel)] = {
    val artifactConfigs = config.getObjectList("artifacts").asScala
    artifactConfigs.foldLeft(List.empty[(String, ArtifactModel)]){ (list, ol) =>
      val c = ol.toConfig
      val artifactName = c.get[String]("name")
      val artifact = ArtifactModel(
        name = artifactName,
        minified = c.getOpt[Boolean]("minified").getOrElse(defaults.artifact.minified),
        s3Bucket = c.getOpt[String]("s3Bucket").getOrElse(defaults.artifact.s3Bucket)
      )
      list :+ (artifactName -> artifact)
    }
  }

  require(artifacts.size > 0, "artifacts must not be empty")

  val functions: List[(String, FunctionModel)] = {
    config.getObjectList("functions").asScala.foldLeft(List.empty[(String, FunctionModel)]) { (list, ol) =>
      val c = ol.toConfig
      val functionName = c.get[String]("name")
      val function = FunctionModel(
        name = functionName,
        artifact = c.getOpt[String]("artifact").map { artifactName =>
          artifacts.find(_._1 == artifactName).map(_._2).getOrElse(sys.error(s"Artifact($artifactName) not found"))
        },
        region = c.getOpt[String]("region").map(RegionUtils.getRegion(_)).getOrElse(defaults.region),
        role = c.getOpt[String]("role").getOrElse(defaults.role),
        description = c.getOpt[String]("description"),
        memory = c.getOpt[Int]("memory").getOrElse(defaults.memory),
        timeout = c.getOpt[Int]("timeout").getOrElse(defaults.timeout),
        handler = FunctionHandlerModel(
          // TODO: Can we access user's class loader? So, we can validate the class information.
          className = c.get[String]("handler.className"),
          methodName = c.get[String]("handler.methodName"),
          returnType = c.getOpt[String]("handler.returnType").getOrElse("void")
        )
      )
      list :+ (functionName -> function)
    }
  }

  require(functions.size > 0, "functions must not be empty")
}

object TestThis extends App {
  val conf = SeverusConfig.parseFile(new File("severus.conf"))
  println(conf.defaults.role)
//  println(conf.functions("f1").artifact)
//  println(conf.config.getString("defaults.tests[1].id"))
}