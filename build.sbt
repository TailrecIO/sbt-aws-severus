name := "sbt-aws-fun"

organization := "io.tailrec.sbt"

version := "0.6.0"

sbtPlugin := true

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.3")

val awsSdkVersion = "1.11.26"

libraryDependencies ++= Seq(
  "com.amazonaws"  % "aws-java-sdk-iam"    % awsSdkVersion,
  "com.amazonaws"  % "aws-java-sdk-lambda" % awsSdkVersion,
  "com.amazonaws"  % "aws-java-sdk-s3"     % awsSdkVersion
)

javaVersionPrefix in javaVersionCheck := Some("1.8")
