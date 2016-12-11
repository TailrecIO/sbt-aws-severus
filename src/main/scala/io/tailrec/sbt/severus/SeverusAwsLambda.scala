package io.tailrec.sbt.severus

import com.amazonaws.regions.Region
import com.amazonaws.services.lambda.{AWSLambdaClient}
import com.amazonaws.services.lambda.model._
import scala.util.{Success, Try}

class SeverusAwsLambda(region: Region) extends SeverusAws {

  private val client: AWSLambdaClient = new AWSLambdaClient(credentialsProvider)
  client.setRegion(region)

  private def isNewFunction(functionName: String, qualifier: Option[String] = None): Try[Boolean] = {
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

  private def createFunction(functionName: String, handlerName: String, roleArn: String,
                   s3BucketName: String, s3Key: String,
                   timeout: Option[Int], memorySize: Option[Int]): Try[CreateFunctionResult] = Try {
    val request = new CreateFunctionRequest()
    request.setFunctionName(functionName)
    request.setHandler(handlerName)
    request.setRole(roleArn)
    request.setRuntime(com.amazonaws.services.lambda.model.Runtime.Java8)
    timeout.map(request.setTimeout(_))
    memorySize.map(request.setMemorySize(_))
    val functionCode = {
      val code = new FunctionCode
      code.setS3Bucket(s3BucketName)
      code.setS3Key(s3Key)
      code
    }
    request.setCode(functionCode)
    val createResult = client.createFunction(request)
    println(s"Created Lambda: ${createResult.getFunctionArn}")
    createResult
  }

  private def updateFunction(functionName: String, handlerName: String, roleArn: String,
                             s3BucketName: String, s3Key: String,
                             timeout: Option[Int], memorySize: Option[Int]): Try[UpdateFunctionCodeResult] = Try {

    def updateFunctionConfiguration() = {
      val request = new UpdateFunctionConfigurationRequest
      request.setFunctionName(functionName)
      request.setHandler(handlerName)
      request.setRole(roleArn)
      timeout.map(request.setTimeout(_))
      memorySize.map(request.setMemorySize(_))
      client.updateFunctionConfiguration(request)
    }

    updateFunctionConfiguration()

    val request = new UpdateFunctionCodeRequest()
    request.setFunctionName(functionName)
    request.setS3Bucket(s3BucketName)
    request.setS3Key(s3Key)
    val updateResult = client.updateFunctionCode(request)

    println(s"Updated lambda ${updateResult.getFunctionArn}")
    updateResult
  }

  def undeployFunction(functionName: String, qualifier: Option[String] = None): Try[Unit] = {
    isNewFunction(functionName, qualifier).flatMap { isNew =>
      if(!isNew) {
        Try{
          val request = new DeleteFunctionRequest()
          request.setFunctionName(functionName)
          qualifier.map(request.setQualifier(_))
          client.deleteFunction(request)
        }
      } else {
        Success()
      }
    }
  }

  def deployFunction(functionName: String,
                     handlerName: String,
                     roleArn: String,
                     s3BucketName: String,
                     s3Key: String,
                     timeout: Option[Int],
                     memorySize: Option[Int]): Try[Either[CreateFunctionResult, UpdateFunctionCodeResult]] = {
    isNewFunction(functionName).flatMap { isNew =>
      if(isNew) {
        createFunction(functionName, handlerName, roleArn, s3BucketName, s3Key, timeout, memorySize).map(Left(_))
      } else {
        updateFunction(functionName, handlerName, roleArn, s3BucketName, s3Key, timeout, memorySize).map(Right(_))
      }
    }
  }

}
