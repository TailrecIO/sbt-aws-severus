package io.tailrec.sbt.severus.config

import java.io.File

import com.amazonaws.regions.Region
import ConfigValidators._

/**
  * @author Hussachai Puripunpinyo
  */
object ConfigModels {

  case class RoleModel(
    name: String,
    policy: String
  )

  case class FunctionHandlerModel(
    className: String,
    methodName: String,
    returnType: String
  ) {
    def toAWSLambdaHandler: String = className + "::" + methodName
  }

  case class LambdaArtifact(jarFile: File, groupName: Option[String] = None, private val minified: Boolean = true) {

    val artifactName: String = groupName.getOrElse(jarFile.getName).stripSuffix(".jar")

    val isMinified: Boolean = groupName.isDefined || minified

    val artifactFile: File = {
      val min = if(isMinified) ".min" else ""
      new File(s"${jarFile.getParent}${File.separator}$artifactName$min.jar")
    }

  }

  case class ArtifactGroupModel (
    id: String,
    s3Region: Region,
    s3Bucket: String
  )

  case class FunctionModel (
    id: String,
    name: String,
    artifactGroup: Option[String],
    region: Region,
    role: String,
    description: Option[String],
    memory: Int,
    timeout: Int,
    handler: FunctionHandlerModel
  ) {
    Lambda.validateFunctionName(name)
    Lambda.validateMemory(name, memory)
    Lambda.validateTimeout(name, timeout)
  }

}
