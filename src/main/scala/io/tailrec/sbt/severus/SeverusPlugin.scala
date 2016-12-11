package io.tailrec.sbt.severus

import java.io.File

import com.amazonaws.regions.{Region, RegionUtils, Regions}
import Minifier.SourceJar
import sbt.{File => _, _}
import sbt.Keys._
import sbt.AutoPlugin
import sbtassembly.AssemblyPlugin.autoImport._

import scala.util.{Failure, Success}
import sbt.complete._
import complete.DefaultParsers._
import io.tailrec.sbt.severus.SeverusModels.FunctionModel

object SeverusPlugin extends AutoPlugin {

  val PluginName = "sbt-aws-fun"

  object AutoImport {

    val config = settingKey[SeverusConfig]("Path of configuration file")
//    val awsRegion = settingKey[Option[String]]("Name of the AWS region to connect to")
//    val awsS3Bucket = settingKey[Option[String]]("Amazon S3 bucket name where the jar will be uploaded")
//    val awsRoleArn = settingKey[Option[String]]("Amazon Resource Name (ARN) of the IAM role for the Lambda function")
//    val awsLambdaHandlers = settingKey[Seq[(String, String)]]("A sequence of pairs of Lambda function names to handlers")
//    val awsLambdaTimeout = settingKey[Option[Int]]("The Lambda timeout length in seconds (1-300)")
//    val awsLambdaMemorySize = settingKey[Option[Int]]("The amount of memory in MB for the Lambda function (128-1536, multiple of 64)")

//    val proGuardEnabled = settingKey[Boolean]("Enable ProGuard to shrink the output jar file")
//    val proGuardConfig = settingKey[Seq[String]]("ProGuard configuration. The default configuration is overridden by these values")
    val deployFunction = inputKey[Unit]("Package and deploy individual function")
    val deployFunctions = taskKey[Seq[(String, Option[String])]]("Package and deploy the current project to AWS Lambda")
    val undeployFunctions = taskKey[Seq[(String, Boolean)]]("Undeploy the current project from AWS Lambda")
  }

  /* sbt doesn't like AutoImport with capital A */
  val autoImport = AutoImport
  import autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = Seq(

    config := SeverusConfig.parseFile(new File("severus.conf")),
    deployFunction := {
      val funConfig = config.value
      funConfig.functions.find(_._1 == StringBasic.parsed).map { case (id, fn) =>
        val jarFile = sbtassembly.AssemblyKeys.assembly.value.getAbsoluteFile
        val defaultS3Bucket = organization.value + "." + name.value
        val defaultArtifact = SeverusConfig.Artifact(
          id = "default",
          name = "default",
          minified = funConfig.defaults.artifact.minified,
          s3Bucket = funConfig.defaults.artifact.s3Bucket.getO
        )
        deployFunctionTask(fn, jarFile)
      }
    },
    deployFunctions := {
      val jarFile = sbtassembly.AssemblyKeys.assembly.value.getAbsoluteFile
      deployFunctionsTask(
        DeploymentConfig(
          region = config.value,
          s3BucketName = awsS3Bucket.value,
          jarFile,
          lambdaHandlers = awsLambdaHandlers.value,
          roleArn = awsRoleArn.value,
          timeout = awsLambdaTimeout.value,
          memorySize = awsLambdaMemorySize.value
        ),
        ProGuardConfig(
          name = name.value,
          enabled = proGuardEnabled.value,
          config = proGuardConfig.value
        )
      )
    },
    undeployFunctions := undeployFunctionsTask(
      UndeploymentConfig(
        region = awsRegion.value,
        s3BucketName = awsS3Bucket.value,
        jarName = (assemblyJarName in assembly).value,
        lambdaHandlers = awsLambdaHandlers.value
      )
    )
  )

  case class DeploymentConfig(region: Option[String],
                              s3BucketName: Option[String],
                              jarFile: File,
                              lambdaHandlers: Seq[(String, String)],
                              roleArn: Option[String],
                              timeout: Option[Int],
                              memorySize: Option[Int]) {
    val callDefinitions = lambdaHandlers.map{ case (functionName, handler) =>
      CallDefinition(functionName, handler)
    }
  }

  case class ProGuardConfig(name: String, enabled: Boolean, config: Seq[String])

  case class UndeploymentConfig(region: Option[String],
                                s3BucketName: Option[String],
                                jarName: String,
                                lambdaHandlers: Seq[(String, String)])

  private def prepare(config: SeverusConfig, function: FunctionModel, jarFile: File) = {

    val artifact = function.artifact
    val awsS3 = new SeverusAwsS3(function.region)
    val s3Bucket = artifact.s3Bucket
    if(awsS3.getBucket(s3Bucket).isEmpty) {
      println(s"Bucket name ${s3Bucket} does not exist. Creating new bucket... ")
      awsS3.createBucket(s3Bucket) match {
        case Success(bucket) => println(s"Bucket ${s3Bucket} was created successfully.")
        case Failure(e) => sys.error(s"Could not create bucket ${s3Bucket} because of ${e.getMessage}")
      }
    }

    val awsIam = new SeverusAwsIam(function.region)
    if(awsIam.basicLambdaRole.isEmpty) {
      println(s"Could not find the default Role ARN. Creating one...")
      awsIam.createBasicLambdaRole match {
        case Success(role) => println(s"Role ARN: ${awsIam.defaultLambdaRole} was created successfully.")
        case Failure(e) => sys.error(s"Could not create a default role ARN due to ${e.getMessage}")
      }
    }

    val artifactFile = if(artifact.minified) {
      /* This function can throw an exception to terminate the task */
      val handlers = config.functions.filter(_._2.artifact.id == function.artifact.id).map(_._2.handler)
      val minFile = new File(artifact.name)
      runProGuard(artifact.name, jarFile, handlers)
    } else {
      jarFile
    }

    val awsLambda = new SeverusAwsLambda(function.region)
    awsS3.putJar(s3Bucket, artifactFile) match {
      case Success(_) =>
        val s3Key = artifact.name
        for (call <- deployment.callDefinitions) yield {
          val functionName = call.functionName
          val handlerName = call.handler.toAWSLambdaHandler

          lambdaTask.deployFunction(functionName, handlerName, roleArn,
            s3BucketName, s3Key, deployment.timeout, deployment.memorySize) match {
            case Success(Left(createFunctionCodeResult)) =>
              functionName -> Some(createFunctionCodeResult.getFunctionArn)
            case Success(Right(updateFunctionCodeResult)) =>
              functionName -> Some(updateFunctionCodeResult.getFunctionArn)
            case Failure(exception) =>
              println(s"Failed to create lambda function: ${exception.getMessage}\n${exception.getStackTraceString}")
              functionName -> None
          }
        }
      case Failure(e) => sys.error(s"Could not put jar to S3 because of ${e.getMessage}")
    }
  }

  private def deployFunctionsTask(deployment: DeploymentConfig,
                                  proGuard: ProGuardConfig): Seq[(String, Option[String])] = {
    deployment.timeout.map { timeout =>
      require(timeout >= 1 && timeout <= 300, "awsLambdaTimeout must be between 1 and 300")
    }

    deployment.memorySize.map { memSize =>
      require(memSize >= 128 && memSize <= 1536 && (memSize % 64 == 0),
        "awsLambdaMemorySize must be between 128 and 1536, and it must be multiple of 64")
    }

    val region = resolveRegion(deployment.region)
    val lambdaTask = new SeverusAwsLambda(region)
    val awsS3 = new SeverusAwsS3(region)

    val result = for {
      s3BucketName <- resolveAwsS3BucketName(deployment.s3BucketName, awsS3)
      awsIam = new SeverusAwsIam(region)
      roleArn <- resolveAwsRoleArn(deployment.roleArn, awsIam)
    } yield {
      val jarFile = if(proGuard.enabled) {
        /* This function can throw an exception to terminate the task */
        runProGuard(proGuard.name, deployment.jarFile, deployment.callDefinitions, proGuard.config)
      } else {
        deployment.jarFile
      }
      awsS3.putJar(s3BucketName, jarFile) match {
        case Success(_) =>
          val s3Key = deployment.jarFile.getName
          for (call <- deployment.callDefinitions) yield {
            val functionName = call.functionName
            val handlerName = call.handler.toAWSLambdaHandler

            lambdaTask.deployFunction(functionName, handlerName, roleArn,
              s3BucketName, s3Key, deployment.timeout, deployment.memorySize) match {
              case Success(Left(createFunctionCodeResult)) =>
                functionName -> Some(createFunctionCodeResult.getFunctionArn)
              case Success(Right(updateFunctionCodeResult)) =>
                functionName -> Some(updateFunctionCodeResult.getFunctionArn)
              case Failure(exception) =>
                println(s"Failed to create lambda function: ${exception.getMessage}\n${exception.getStackTraceString}")
                functionName -> None
            }
          }
        case Failure(e) => sys.error(s"Could not put jar to S3 because of ${e.getMessage}")
      }
    }

    result.getOrElse(Nil)
  }

  /**
    * Run ProGuard on the input jar using provided configurations.
    * This function throws an exception when ProGuard fails, and the task must be terminated.
    *
    * @return the minified jar file
    */
  private def runMinifier(intFile: File,
                          outFile: String,
                          handlers: List[SeverusConfig.FunctionHandler]): File = {
    println("Running minifier...")
    val fileName = s"${name}.pro"
    Minifier.writeConfig(fileName, SourceJar(intFile), handlers) match {
      case Success(outFile) =>
        Minifier.minify(fileName) match {
          case Success(_) =>
            println(s"The file was minified successfully and saved to ${outFile}")
            outFile
          case Failure(e) =>
            sys.error(s"Minification error: ${e.getMessage}")
        }
      case Failure(e) =>
        sys.error(s"Failed to create the ProGuard config due to ${e}")
    }
  }

  private def undeployFunctionsTask(undeployment: UndeploymentConfig): Seq[(String, Boolean)] = {

    val region = resolveRegion(undeployment.region)
    val lambdaTask = new SeverusAwsLambda(region)
    val awsS3 = new SeverusAwsS3(region)

    resolveAwsS3BucketName(undeployment.s3BucketName, awsS3) map { s3BucketName =>
      val result = for ((functionName, handlerName) <- undeployment.lambdaHandlers) yield {
        lambdaTask.undeployFunction(functionName) match {
          case Success(_) => functionName -> true
          case Failure(e) =>
            println(s"Could not undeploy function ${functionName} due to ${e.getMessage}")
            functionName -> false
        }
      }
      val (successes, errors) = result.partition(_._2 == true)
      if(result.isEmpty || errors.size == 0){
        awsS3.deleteJar(s3BucketName, undeployment.jarName) match {
          case Success(_) => println(s"Undeployed successfully!")
          case Failure(e) => println(s"All functions were undeployed but the jar file was not deleted due to ${e.getMessage}")
        }
      } else {
        if(successes.nonEmpty) {
          println(s"These functions were undeployed successfully:")
          successes.foreach(s => println(s" - ${s._1}"))
        }
        if(errors.nonEmpty) {
          println(s"Could not undeploy these functions:")
          errors.foreach(s => println(s" - ${s._1}"))
        }
      }
      result
    } getOrElse(Nil)
  }

  private def resolveRegion(settingValue: Option[String]): Region = {
    settingValue.map(RegionUtils.getRegion(_)) getOrElse {
      val defaultRegion: Region = RegionUtils.getRegion(Regions.US_EAST_1.getName)
      println(s"Region is not defined. Use default region: ${defaultRegion}")
      defaultRegion
    }
  }

  private def resolveAwsS3BucketName(settingValue: Option[String], awsS3: SeverusAwsS3): Option[String] = {
    settingValue flatMap { bucketName =>
      awsS3.getBucket(bucketName).map(_.getName).orElse{
        println(s"Bucket name ${bucketName} does not exist. Creating new bucket... ")
        awsS3.createBucket(bucketName) match {
          case Success(bucket) =>
            println(s"Bucket ${bucketName} was created successfully.")
            Some(bucket.getName)
          case Failure(e) => {
            println(s"Could not create bucket ${bucketName} because of ${e.getMessage}")
            None
          }
        }
      }
    } orElse {
      println(s"awsS3Bucket setting is required by ${PluginName}")
      None
    }
  }

  private def resolveAwsRoleArn(settingValue: Option[String], awsIam: SeverusAwsIam): Option[String] = {
    settingValue orElse {
      println(s"awsRoleArn is not defined. Finding the default one...")
      awsIam.basicLambdaRole.map(_.getArn) orElse {
        println(s"Could not find the default Role ARN. Creating one...")
        awsIam.createBasicLambdaRole match {
          case Success(role) =>
            println(s"Role ARN: ${awsIam.defaultLambdaRole} was created successfully.")
            Some(role.getArn)
          case Failure(e) =>
            println(s"Could not create a default role ARN due to ${e.getMessage}")
            None
        }
      }
    }
  }

}
