package io.tailrec.sbt.awsfn

import com.amazonaws.regions.Region
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model._
import sbt.File
import scala.util.Try

class AwsS3Service(region: Region) extends AwsService {

  private lazy val client = new AmazonS3Client(credentialsProvider)
  client.setRegion(region)

  def putJar(bucketName: String, jar: File): Try[PutObjectResult] = Try {
    println(s"Uploading jar: ${jar.getName} to S3 bucket: ${bucketName}")
    val request = new PutObjectRequest(bucketName, jar.getName, jar)
    request.setCannedAcl(CannedAccessControlList.AuthenticatedRead)
    client.putObject(request)
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
