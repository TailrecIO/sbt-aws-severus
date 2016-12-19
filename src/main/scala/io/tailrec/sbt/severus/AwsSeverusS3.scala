package io.tailrec.sbt.severus

import java.io.{File, FileInputStream}

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import io.tailrec.sbt.severus.config.S3Config
import org.apache.commons.codec.digest.DigestUtils
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * @author Hussachai Puripunpinyo
  */
class AwsSeverusS3(config: S3Config) extends AwsSeverus {

  private val client = new AmazonS3Client(credentialsProvider)
  client.setRegion(config.region)

  val metaChecksum = "checksum"

  def putFile(bucketName: String, file: File): Try[Option[PutObjectResult]] = Try {
    if(compareChecksum(bucketName, file)){
      println(s"Remote checksum and computed checksum match!")
      None
    } else {
      println(s"Uploading file: ${file.getName} to S3 bucket: ${bucketName}...")
      val request = new PutObjectRequest(bucketName, file.getName, file)
      request.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
      calculateChecksum(file){ checksum =>
        val metaData = new ObjectMetadata()
        metaData.addUserMetadata(metaChecksum, checksum)
        request.setMetadata(metaData)
      }
      Option(client.putObject(request))
    }
  }

  private def calculateChecksum[T](jar: File)(fn: String => T): Option[T] = {
    Resources.using(new FileInputStream(jar)) { in =>
      Try(DigestUtils.md5Hex(in)) match {
        case Success(checksum) =>
          Some(fn(checksum))
        case Failure(e) =>
          println(s"Could not calculate checksum due to ${e.getMessage}")
          None
      }
    }
  }

  private def compareChecksum(bucketName: String, file: File): Boolean = {
    calculateChecksum(file)(c => c).flatMap { checksum =>
      println(s"Local file checksum: ${checksum}")
      Try(client.getObjectMetadata(bucketName, file.getName)).toOption.flatMap{ meta =>
        Option(meta.getUserMetaDataOf(metaChecksum))
      }.map { remoteChecksum =>
        println(s"Remote file checksum: ${remoteChecksum}")
        remoteChecksum == checksum
      }
    }.getOrElse{
      println(s"Failed to compare checksum!")
      false
    }
  }

  def deleteFile(bucketName: String, fileName: String): Try[Unit] = Try {
    println(s"Deleting file: ${fileName} from S3 bucket: ${bucketName}")
    val request = new DeleteObjectRequest(bucketName, fileName)
    client.deleteObject(request)
  }

  def getBucket(bucketName: String): Option[Bucket] = {
    client.listBuckets().asScala.find(_.getName == bucketName)
  }

  def createBucket(bucketName: String, region: Region): Try[Bucket] = Try {
    val request = new CreateBucketRequest(bucketName)
    request.setRegion(region.getName)
    request.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
    client.createBucket(bucketName)
  }

  def shutdown(): Unit = client.shutdown()

}
