package io.tailrec.sbt.severus

import com.amazonaws.auth.{AWSCredentialsProvider, DefaultAWSCredentialsProviderChain}

trait SeverusAws {

  lazy val credentialsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain()

}
