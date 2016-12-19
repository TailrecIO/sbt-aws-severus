package io.tailrec.sbt.severus

import java.io.File

import com.amazonaws.regions.Region
import sbt.{File, Future => _, _}
import sbt.Keys._
import sbt.AutoPlugin
import sbtassembly.AssemblyPlugin.autoImport._

import scala.util.{Failure, Success}
import sbt.complete._
import complete.DefaultParsers._
import io.tailrec.sbt.severus.config.ConfigModels.{FunctionModel, LambdaArtifact}
import io.tailrec.sbt.severus.config.SeverusConfig
import org.apache.commons.codec.digest.DigestUtils

/**
  * @author Hussachai Puripunpinyo
  */
object AwsSeverusPlugin extends AutoPlugin {

  val PluginName = "sbt-aws-fun"

  object AutoImport {

    val severusConfig = settingKey[String]("A file path of a configuration")
    val createConfig = taskKey[Unit]("Generate a configuration template")
    val deployFunction = inputKey[Unit]("Package and deploy the specified function")
    val deployAllFunctions = taskKey[Unit]("Package and deploy the current project to AWS Lambda")
    val undeployFunction = inputKey[Unit]("Undeploy the specified function")
    val undeployAllFunctions = taskKey[Unit]("Undeploy the current project from AWS Lambda")
  }

  /* sbt doesn't like AutoImport with capital A */
  val autoImport = AutoImport
  import autoImport._

  override def requires = sbtassembly.AssemblyPlugin

  override lazy val projectSettings = Seq(
    severusConfig := SeverusConfig.FileName,
    createConfig := {
      val configFile = new File(SeverusConfig.FileName)
      println(s"Creating configuration file: ${configFile}")
      val configTemplate = Resources.readResourceAsString("classpath:template.conf")
      Resources.writeStringToFile(configFile, configTemplate)
    },
    deployFunction := {
      val jarFile = assembly.value.getAbsoluteFile
      deployOneFunctionTask(createServiceFromConfig(severusConfig.value), jarFile, StringBasic.parsed)
    },
    deployAllFunctions := {
      val jarFile = assembly.value.getAbsoluteFile
      deployAllFunctionsTask(createServiceFromConfig(severusConfig.value), jarFile)
    },
    undeployFunction := {
      println("To be implemented")
    },
    undeployAllFunctions := {
      println("To be implemented")
    },
    clean <<= clean.map { _ =>
      new File("artifact.md5").delete()
    },
    test in assembly := {} // disabled test on assembly
  )

  private def createServiceFromConfig(filePath: String): AwsSeverusService = {
    new AwsSeverusService(SeverusConfig.parseFile(new File(filePath)))
  }

  private def prepareOneArtifact(service: AwsSeverusService, jarFile: File, functionId: String): Unit = {
    prepareArtifacts(service, jarFile) {
      service.config.lambda.functions.groupBy(_.artifactGroup).find { case (_, functions) =>
        functions.exists(_.id == functionId)
      }.map(a => Map(a._1 -> a._2)).getOrElse {
        Map.empty[Option[String], List[FunctionModel]]
      }
    }
  }

  private def prepareAllArtifacts(service: AwsSeverusService, jarFile: File): Unit = {
    prepareArtifacts(service, jarFile) {
      service.config.lambda.functions.groupBy(_.artifactGroup)
    }
  }

  private def prepareArtifacts(service: AwsSeverusService, jarFile: File)
    (groupedArtifacts: => Map[Option[String], List[FunctionModel]]): Unit = {
    // TODO: the artifact file cannot have the same name as the original jar file
    // TODO: what should we do with S3 region?
    val artifactInfoFile = new File("artifact.md5")
    val chksum = Resources.readFile(jarFile)(DigestUtils.md5Hex(_))
    val fnSig = DigestUtils.md5Hex(service.config.lambda.functions.map(_.handler.toString).mkString("|"))
    val artifactUpdated = if(artifactInfoFile.exists()) {
      val artifactData = Resources.readFileAsString(artifactInfoFile).split("\n")
      (chksum != artifactData(0)) || (fnSig != artifactData(1))
    } else true

    if(artifactUpdated) {
      for {
        (artifactGroupOpt, functions) <- groupedArtifacts
      } yield {
          val artifact = LambdaArtifact(jarFile, artifactGroupOpt, service.config.lambda.minified)
          println(s"Preparing artifact: ${artifact.artifactName}")

          if(artifact.isMinified) {
            ArtifactMinifier.minify(artifact, functions.map(_.handler))
          }

          val s3BucketName = service.s3.getBucket(service.config.lambda.s3Bucket).map(_.getName).getOrElse {

            val (region: Region, bucketName: String) = artifactGroupOpt.flatMap { artifactGroupId =>
              service.config.lambda.artifacts.get(artifactGroupId).map { artifactGroupModel =>
                (artifactGroupModel.s3Region, artifactGroupModel.s3Bucket)
              }
            }.getOrElse((service.config.lambda.default.region, service.config.lambda.s3Bucket))

            println(s"Creating new bucket: ${service.config.lambda.s3Bucket}")
            service.s3.createBucket(bucketName, region) match {
              case Success(bucket) => bucket.getName
              case Failure(e) => sys.error(s"Could not create bucket due to: ${e.getMessage}")
            }
          }
          println(service.s3.putFile(s3BucketName, artifact.artifactFile))
      }
    } else {
      println("All artifacts are up-to-date!")
    }

    // Update jarFile checksum
    if (artifactUpdated) {
      println(s"Writing artifact information to file: ${artifactInfoFile.getName}")
      Resources.writeStringToFile(artifactInfoFile, chksum + "\n" + fnSig)
    }
  }

  private def deployLambdaFunction(service: AwsSeverusService, jarFile: File, functionModel: FunctionModel): Unit = {
    val artifact = LambdaArtifact(jarFile, functionModel.artifactGroup, service.config.lambda.minified)
    service.lambda.deployFunction(functionModel, artifact) match {
      case Success(result) =>
      case Failure(e) => println(s"\t${e.getMessage}")
    }
  }

  private def deployOneFunctionTask(service: AwsSeverusService, jarFile: File, functionId: String) = {

    prepareOneArtifact(service, jarFile, functionId)
    val functionModel = service.config.lambda.functions.find(_.id == functionId).getOrElse {
      sys.error(s"Function: $functionId not found")
    }
    deployLambdaFunction(service, jarFile, functionModel)
  }

  private def deployAllFunctionsTask(service: AwsSeverusService, jarFile: File): Unit = {
    prepareAllArtifacts(service, jarFile)
    service.config.lambda.functions.foreach { functionModel =>
      deployLambdaFunction(service, jarFile, functionModel)
    }
  }

}
