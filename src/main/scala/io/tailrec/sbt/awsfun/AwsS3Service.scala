package io.tailrec.sbt.awsfn

import java.io.FileInputStream
import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import org.apache.commons.codec.digest.DigestUtils
import sbt.File

import scala.util.{Failure, Success, Try}

class AwsS3Service(region: Region) extends AwsService {

  private val client = new AmazonS3Client(credentialsProvider)
  client.setRegion(region)

  val metaChecksum = "checksum"

  def putJar(bucketName: String, jar: File): Try[Option[PutObjectResult]] = Try {
    if(compareChecksum(bucketName, jar)){
      println(s"Remote checksum and computed checksum match!")
      None
    } else {
      println(s"Uploading jar: ${jar.getName} to S3 bucket: ${bucketName}...")
      val request = new PutObjectRequest(bucketName, jar.getName, jar)
      request.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
      calculateChecksum(jar){ checksum =>
        val metaData = new ObjectMetadata()
        metaData.addUserMetadata(metaChecksum, checksum)
        request.setMetadata(metaData)
      }
      Option(client.putObject(request))
    }
  }

  private def calculateChecksum[T](jar: File)(fn: String => T): Option[T] = {
    val in = new FileInputStream(jar.getAbsoluteFile)
    val result = Try(DigestUtils.md5Hex(in)) match {
      case Success(checksum) =>
        Some(fn(checksum))
      case Failure(e) =>
        println(s"Could not calculate checksum due to ${e.getMessage}")
        None
    }
    in.close
    result
  }

  private def compareChecksum(bucketName: String, jar: File): Boolean = {
    calculateChecksum(jar)(c => c).flatMap { checksum =>
      println(s"Local file checksum: ${checksum}")
      Try(client.getObjectMetadata(bucketName, jar.getName)).toOption.flatMap{ meta =>
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

  def deleteJar(bucketName: String, jarName: String): Try[Unit] = Try {
    println(s"Deleting jar: ${jarName} from S3 bucket: ${bucketName}")
    val request = new DeleteObjectRequest(bucketName, jarName)
    client.deleteObject(request)
  }

  def getBucket(bucketName: String): Option[Bucket] = {
    import scala.collection.JavaConverters._
    client.listBuckets().asScala.find(_.getName == bucketName)
  }

  def createBucket(bucketName: String): Try[Bucket] = Try {
    val request = new CreateBucketRequest(bucketName)
    request.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
    client.createBucket(bucketName)
  }

}
