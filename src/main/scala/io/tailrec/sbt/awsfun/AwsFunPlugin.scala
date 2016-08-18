package io.tailrec.sbt.awsfun

import com.amazonaws.regions.{Region, RegionUtils, Regions}
import sbt._
import sbt.AutoPlugin

import scala.util.{Failure, Success}

object AwsFunPlugin extends AutoPlugin {

  val PluginName = "sbt-aws-fun"

  object AutoImport {

    val awsRegion = settingKey[Option[String]]("Name of the AWS region to connect to")
    val awsS3Bucket = settingKey[Option[String]]("Amazon S3 bucket name where the jar will be uploaded")
    val awsRoleArn = settingKey[Option[String]]("Amazon Resource Name (ARN) of the IAM role for the Lambda function")
    val awsLambdaHandlers = settingKey[Seq[(String, String)]]("A sequence of pairs of Lambda function names to handlers")
    val awsLambdaTimeout = settingKey[Option[Int]]("The Lambda timeout length in seconds (1-300)")
    val awsLambdaMemorySize = settingKey[Option[Int]]("The amount of memory in MB for the Lambda function (128-1536, multiple of 64)")

    val deployFunctions = taskKey[Seq[(String, Option[String])]]("Package and deploy the current project to AWS Lambda")
    val undeployFunctions = taskKey[Seq[(String, Boolean)]]("Undeploy the current project from AWS Lambda")
  }

  /* sbt doesn't like AutoImport with capital A */
  val autoImport = AutoImport
  import autoImport._
  import sbtassembly.AssemblyPlugin.autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = {

    Seq(
      awsRegion := None,
      awsS3Bucket := None,
      awsRoleArn := None,
      awsLambdaHandlers := Seq.empty[(String, String)],
      awsLambdaTimeout := None,
      awsLambdaMemorySize := None,
      deployFunctions := deployFunctionsTask(
        regionOpt = awsRegion.value,
        s3BucketNameOpt = awsS3Bucket.value,
        jarFile = sbtassembly.AssemblyKeys.assembly.value,
        lambdaHandlers = awsLambdaHandlers.value,
        roleArnOpt = awsRoleArn.value,
        timeoutOpt = awsLambdaTimeout.value,
        memorySizeOpt = awsLambdaMemorySize.value
      ),
      undeployFunctions := undeployFunctionsTask(
        regionOpt = awsRegion.value,
        s3BucketNameOpt = awsS3Bucket.value,
        jarName = (assemblyJarName in assembly).value,
        lambdaHandlers = awsLambdaHandlers.value
      )
    )
  }

  private def deployFunctionsTask(regionOpt: Option[String],
                                 s3BucketNameOpt: Option[String],
                                 jarFile: File,
                                 lambdaHandlers: Seq[(String, String)],
                                 roleArnOpt: Option[String],
                                 timeoutOpt: Option[Int],
                                 memorySizeOpt: Option[Int]): Seq[(String, Option[String])] = {
    timeoutOpt.map { timeout =>
      require(timeout >= 1 && timeout <= 300, "awsLambdaTimeout must be between 1 and 300")
    }

    memorySizeOpt.map { memSize =>
      require(memSize >= 128 && memSize <= 1536 && (memSize % 64 == 0),
        "awsLambdaMemorySize must be between 128 and 1536, and it must be multiple of 64")
    }

    val region = resolveRegion(regionOpt)
    val lambdaTask = new AwsLambdaService(region)
    val awsS3 = new AwsS3Service(region)

    val result = for {
      s3BucketName <- resolveAwsS3BucketName(s3BucketNameOpt, awsS3)
      awsIam = new AwsIamService(region)
      roleArn <- resolveAwsRoleArn(roleArnOpt, awsIam)
    } yield {
      awsS3.putJar(s3BucketName, jarFile) match {
        case Success(_) =>
          val s3Key = jarFile.getName
          for ((functionName, handlerName) <- lambdaHandlers) yield {
            lambdaTask.deployFunction(functionName, handlerName, roleArn,
              s3BucketName, s3Key, timeoutOpt, memorySizeOpt) match {
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

  private def undeployFunctionsTask(regionOpt: Option[String],
                                   s3BucketNameOpt: Option[String],
                                   jarName: String,
                                   lambdaHandlers: Seq[(String, String)]): Seq[(String, Boolean)] = {

    val region = resolveRegion(regionOpt)
    val lambdaTask = new AwsLambdaService(region)
    val awsS3 = new AwsS3Service(region)

    resolveAwsS3BucketName(s3BucketNameOpt, awsS3) map { s3BucketName =>
      val result = for ((functionName, handlerName) <- lambdaHandlers) yield {
        lambdaTask.undeployFunction(functionName) match {
          case Success(_) => functionName -> true
          case Failure(e) =>
            println(s"Could not undeploy function ${functionName} due to ${e.getMessage}")
            functionName -> false
        }
      }
      val (successes, errors) = result.partition(_._2 == true)
      if(result.isEmpty || errors.size == 0){
        awsS3.deleteJar(s3BucketName, jarName) match {
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

  private def resolveAwsS3BucketName(settingValue: Option[String], awsS3: AwsS3Service): Option[String] = {
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

  private def resolveAwsRoleArn(settingValue: Option[String], awsIam: AwsIamService): Option[String] = {
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
