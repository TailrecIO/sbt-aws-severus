package io.tailrec.sbt.severus

import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient
import com.amazonaws.services.identitymanagement.model.{CreateRoleRequest, Role}
import io.tailrec.sbt.severus.config.IamConfig

import scala.collection.JavaConverters._
import scala.util.Try

class AwsSeverusIam(config: IamConfig) extends AwsSeverus {

  val defaultLambdaRole = "lambda_basic_execution"

  private val client = new AmazonIdentityManagementClient(credentialsProvider)
  client.setRegion(config.region)

  def findRole(name: String): Option[Role] = {
    val existingRoles = client.listRoles().getRoles.asScala
    existingRoles.find(_.getRoleName == name)
  }

  def basicLambdaRole(): Option[Role] = findRole(defaultLambdaRole)

  def createBasicLambdaRole(): Try[Role] = Try {
    println(s"Creating a new IAM role:  ${defaultLambdaRole}")
    val policyDocument = """{"Version":"2012-10-17","Statement":[{"Sid":"","Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}"""
    val request = new CreateRoleRequest
    request.setRoleName(defaultLambdaRole)
    request.setAssumeRolePolicyDocument(policyDocument)
    client.createRole(request).getRole
  }

  def shutdown(): Unit = client.shutdown()

}
