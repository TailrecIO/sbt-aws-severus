package io.tailrec.sbt.awsfun

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}

trait AwsService {

  lazy val credentialsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain()

}
