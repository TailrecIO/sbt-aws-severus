name := "sbt-aws-severus"

organization := "io.tailrec.sbt"

version := "1.0.0-SNAPSHOT"

sbtPlugin := true

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

val awsSdkVersion = "1.11.26"
val proguardVersion = "5.2.1"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.1",
  "com.amazonaws" % "aws-java-sdk-iam" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-lambda" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-s3" % awsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-api-gateway" % awsSdkVersion,
  "net.sf.proguard" % "proguard-base" % proguardVersion
)

javaVersionPrefix in javaVersionCheck := Some("1.8")

