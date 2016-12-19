package io.tailrec.sbt.severus

import io.tailrec.sbt.severus.config.SeverusConfig

/**
  * @author Hussachai Puripunpinyo
  */
class AwsSeverusService (val config: SeverusConfig) {

  lazy val iam = new AwsSeverusIam(config.iam)

  lazy val s3 = new AwsSeverusS3(config.s3)

  lazy val lambda = new AwsSeverusLambda(config.lambda)(iam)

}
