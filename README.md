# sbt-aws-fn

sbt plugin to manage functions to AWS Lambda

This plugin was inspired by [sbt-aws-lambda](https://github.com/gilt/sbt-aws-lambda). Thanks to their great work!

Installation
------------

Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("io.tailrec.sbt" % "sbt-aws-fn" % "0.5.0")
```

Add the `AwsFnPlugin` auto-plugin to your build.sbt:

```scala
enablePlugins(AwsFnPlugin)
```


Usage
-------------
`sbt deployFunctions` deploy new AWS Lambda function(s) from the current project.

`sbt undeployFunctions` undeploy existing AWS Lambda function(s) from the current project.


Configuration
-------------

sbt-aws-fn can be configured using sbt settings

| sbt setting         |   Description |
|:--------------------|:--------------|
| awsS3Bucket         | The name of an S3 bucket where the lambda code will be stored |
| awsRoleArn          | The [ARN](http://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html "AWS ARN documentation") of an [IAM](https://aws.amazon.com/iam/ "AWS IAM documentation") role to use when creating a new Lambda |
| awsRegion           | The name of the AWS region to connect to. Defaults to `us-east-1` |
| awsLambdaTimeout    | The Lambda timeout in seconds (1-300). Defaults to AWS default. |
| awsLambdaMemorySize | The amount of memory in MB for the Lambda function (128-1536, multiple of 64). Defaults to AWS default. |
| awsLambdaHandlers   | Sequence of Lambda names to handler functions. |

An example configuration might look like this:

```scala
retrieveManaged := true

enablePlugins(AwsFnPlugin)

awsLambdaHandlers := Seq(
  "function1"                 -> "io.tailrec.example.Lambda::handleRequest1",
  "function2"                 -> "io.tailrec.example.Lambda::handleRequest2",
  "function3"                 -> "io.tailrec.example.Lambda::handleRequest3"
)

awsS3Bucket := Some("lambda-jars")

awsLambdaMemorySize := Some(192)

awsLambdaTimeout := Some(30)

awsRoleArn := Some("arn:aws:iam::123456789000:role/lambda_basic_execution")

```
Note that you can omit `awsRoleArn` to use the default one which is `lambda_basic_execution`


Publishing new versions of this plugin
--------------------------------------

This plugin uses [sbt-sonatype](https://github.com/xerial/sbt-sonatype) to publish to TailrecIO's account on maven central

```
sbt publishSigned sonatypeRelease
```