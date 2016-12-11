package io.tailrec.sbt.severus

import com.amazonaws.regions.Region
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{CreateRoleRequest, CreateRoleResult, Role}

import scala.util.Try

class SeverusAwsIam(region: Region) extends SeverusAws {

  val defaultLambdaRole = "lambda_basic_execution"

  private val client = new AmazonIdentityManagementClient(credentialsProvider)
  client.setRegion(region)

  def basicLambdaRole(): Option[Role] = {
    import scala.collection.JavaConverters._
    val existingRoles = client.listRoles().getRoles.asScala
    existingRoles.find(_.getRoleName == defaultLambdaRole)
  }

  def createBasicLambdaRole(): Try[Role] = Try {
    println(s"Creating a new IAM role:  ${defaultLambdaRole}")
    val policyDocument = """{"Version":"2012-10-17","Statement":[{"Sid":"","Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}"""
    val request = new CreateRoleRequest
    request.setRoleName(defaultLambdaRole)
    request.setAssumeRolePolicyDocument(policyDocument)
    client.createRole(request).getRole
  }

}
