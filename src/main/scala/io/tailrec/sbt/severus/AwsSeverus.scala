package io.tailrec.sbt.severus

import com.amazonaws.AmazonWebServiceClient
import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}

/**
  * @author Hussachai Puripunpinyo
  */
trait AwsSeverus {

  lazy val credentialsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain()

  def execute[S <: AmazonWebServiceClient, T](client : S)(block: S => T): T = {
    try {
      block(client)
    } finally {
      if(client != null) client.shutdown()
    }
  }

  def shutdown(): Unit

}
