package io.tailrec.sbt.severus.config

import com.amazonaws.regions.{Region, RegionUtils}
import com.typesafe.config.Config
import io.tailrec.sbt.severus.config.ConfigModels.{ArtifactGroupModel, FunctionHandlerModel, FunctionModel}

import scala.collection.JavaConverters._
import SeverusConfig._
import ConfigValidators._

/**
  * @author Hussachai Puripunpinyo
  */
class LambdaConfig(config: Config) {

  val minified: Boolean = config.getAsOpt[Boolean]("minified").getOrElse(false)

  val s3Bucket: String = config.getAs[String]("s3Bucket")

  object default {
    private val p = "default"
    val region: Region = RegionUtils.getRegion(config.getAs[String](s"$p.region"))
    val role: String = config.getAsOpt[String](s"$p.role").getOrElse("lambda_basic_execution")
    val memory: Int = config.getAs[Int](s"$p.memory")
    val timeout: Int = config.getAs[Int](s"$p.timeout")

    Lambda.validateMemory("*default", memory)
    Lambda.validateTimeout("*default", timeout)
  }

  val artifacts: Map[String, ArtifactGroupModel] = {
    val emptyMap = Map.empty[String, ArtifactGroupModel]
    if(config.hasPath("artifacts")) {
      config.getObject("artifacts").keySet().asScala.foldLeft(emptyMap) { (m, groupId) =>
        if (config.hasPath(s"artifacts.$groupId")) {
          val c = config.getConfig(s"artifacts.$groupId")
          val region = RegionUtils.getRegion(c.getAs[String]("s3Region"))
          m + (groupId -> ArtifactGroupModel(groupId, region, c.getAs[String]("s3Bucket")))
        } else m
      }
    } else {
      emptyMap
    }
  }

  val functions: List[FunctionModel] = {
    for {
      functionId <- config.getObject("functions").keySet().asScala.toList.sorted
    } yield {
      val c = config.getConfig(s"functions.$functionId")
      FunctionModel(
        id = functionId,
        name = c.getAsOpt[String]("name").getOrElse(functionId),
        artifactGroup = c.getAsOpt[String]("artifactGroup"),
        region = c.getAsOpt[String]("region").map(RegionUtils.getRegion(_)).getOrElse(default.region),
        role = c.getAsOpt[String]("role").getOrElse(default.role),
        description = c.getAsOpt[String]("description"),
        memory = c.getAsOpt[Int]("memory").getOrElse(default.memory),
        timeout = c.getAsOpt[Int]("timeout").getOrElse(default.timeout),
        handler = FunctionHandlerModel(
          // TODO: Can we access user's class loader? So, we can validate the class information.
          className = c.getAs[String]("handler.className"),
          methodName = c.getAs[String]("handler.methodName"),
          returnType = c.getAsOpt[String]("handler.returnType").getOrElse("void")
        )
      )
    }
  }

  if(functions.size == 0)  println("aws.lambda.functions is empty")

}
