package io.tailrec.sbt.severus

import com.amazonaws.regions.Region
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model._
import io.tailrec.sbt.severus.config.ConfigModels.{FunctionModel, LambdaArtifact}
import io.tailrec.sbt.severus.config.LambdaConfig
import scala.util.{Success, Try}

/**
  * @author Hussachai Puripunpinyo
  */
class AwsSeverusLambda(config: LambdaConfig)(iam: AwsSeverusIam) extends AwsSeverus {

  private def createClient(region: Region): AWSLambdaClient = {
    val client = new AWSLambdaClient(credentialsProvider)
    client.setRegion(region)
    client
  }

  private def isNewFunction(client: AWSLambdaClient, functionName: String,
    qualifier: Option[String] = None): Try[Boolean] = {
    Try {
      val request = new GetFunctionRequest
      request.setFunctionName(functionName)
      qualifier.map(request.setQualifier(_))
      client.getFunction(request)
      false
    } recover {
      case _ : ResourceNotFoundException => true
    }
  }

  private def resolveRoleArn(roleName: String): String = {
    if(roleName.startsWith("arn:aws:iam::")) roleName
    else {
      iam.findRole(roleName).map(_.getArn).getOrElse(sys.error(s"Role ARN not found: ${roleName}"))
    }
  }

  private def createFunction(client: AWSLambdaClient, functionModel: FunctionModel,
    artifact: LambdaArtifact): Try[CreateFunctionResult] = Try {
    val request = new CreateFunctionRequest()
    request.setFunctionName(functionModel.name)
    request.setHandler(functionModel.handler.toAWSLambdaHandler)
    request.setRole(resolveRoleArn(functionModel.role))
    request.setRuntime(Runtime.Java8)
    request.setTimeout(functionModel.timeout)
    request.setMemorySize(functionModel.memory)
    functionModel.description.foreach(request.setDescription(_))
    val functionCode = {
      val code = new FunctionCode
      code.setS3Bucket(config.s3Bucket)
      code.setS3Key(artifact.artifactFile.getName)
      code
    }
    request.setCode(functionCode)
    val createResult = client.createFunction(request)
    println(s"Created function: '${functionModel.name}' ARN: '${createResult.getFunctionArn}'")
    createResult
  }

  private def updateFunction(client: AWSLambdaClient, functionModel: FunctionModel,
    artifact: LambdaArtifact): Try[UpdateFunctionCodeResult] = Try {

    def updateFunctionConfiguration() = {
      val request = new UpdateFunctionConfigurationRequest
      request.setFunctionName(functionModel.name)
      request.setHandler(functionModel.handler.toAWSLambdaHandler)
      request.setRole(resolveRoleArn(functionModel.role))
      request.setTimeout(functionModel.timeout)
      request.setMemorySize(functionModel.memory)
      functionModel.description.foreach(request.setDescription(_))
      client.updateFunctionConfiguration(request)
    }

    updateFunctionConfiguration()

    val request = new UpdateFunctionCodeRequest()
    request.setFunctionName(functionModel.name)
    request.setS3Bucket(config.s3Bucket)
    request.setS3Key(artifact.artifactFile.getName)
    val updateResult = client.updateFunctionCode(request)

    println(s"Updated function: '${functionModel.name}' ARN: '${updateResult.getFunctionArn}'")
    updateResult
  }

  def undeployFunction(functionModel: FunctionModel, qualifier: Option[String] = None): Try[Unit] = {
    execute(createClient(functionModel.region)) { client =>
      isNewFunction(client, functionModel.name, qualifier).flatMap { isNew =>
        if (!isNew) {
          Try {
            val request = new DeleteFunctionRequest()
            request.setFunctionName(functionModel.name)
            qualifier.map(request.setQualifier(_))
            client.deleteFunction(request)
          }
        } else {
          Success()
        }
      }
    }
  }

  def deployFunction(functionModel: FunctionModel, artifact: LambdaArtifact):
    Try[Either[CreateFunctionResult, UpdateFunctionCodeResult]] = {
    println(s"Deploying function: '${functionModel.name}' to region: '${functionModel.region}'")
    execute(createClient(functionModel.region)) { client =>
      isNewFunction(client, functionModel.name).flatMap { isNew =>
        if (isNew) {
          createFunction(client, functionModel, artifact).map(Left(_))
        } else {
          updateFunction(client, functionModel, artifact).map(Right(_))
        }
      }
    }
  }

  def shutdown(): Unit = {}
}
