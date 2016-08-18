package io.tailrec.sbt.awsfn

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}

trait AwsService {

  lazy val credentialsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain()

}
