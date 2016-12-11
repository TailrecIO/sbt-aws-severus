package io.tailrec.sbt.severus

import com.amazonaws.regions.Region

object SeverusModels {

  private val FileNamePattern = "^[\\w-][\\w.-]*[\\w]$".r.pattern

  case class RoleModel(
    name: String,
    policy: String
  )

  case class ArtifactModel(
    name: String,
    minified: Boolean,
    s3Bucket: String
  ) {
    require(FileNamePattern.matcher(name).matches(),
      s"Artifact($name) -> name must conform to: ${FileNamePattern.pattern()}")
  }

  case class FunctionHandlerModel(
    className: String,
    methodName: String,
    returnType: String
  )

  case class FunctionModel (
    name: String,
    artifact: Option[ArtifactModel],
    region: Region,
    role: String,
    description: Option[String],
    memory: Int,
    timeout: Int,
    handler: FunctionHandlerModel
  ) {
    require(FileNamePattern.matcher(name).matches(),
      s"Function($name) -> name must conform to: ${FileNamePattern.pattern()}")
    require(memory >= 128 && memory <= 1536 && (memory % 64 == 0),
      s"Function($name) -> memory must be between 128 and 1536, and it must be multiple of 64")
    require(timeout > 0 && timeout <= 300,
      s"Function($name) -> timeout must be between 1 and 300 seconds")
  }
}
