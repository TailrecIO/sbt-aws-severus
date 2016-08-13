# sbt-aws-fn

sbt plugin to manage functions to AWS Lambda

This plugin was inspired by [sbt-aws-lambda](https://github.com/gilt/sbt-aws-lambda). Thanks to their great work!  
The fat jar is created by [sbt-assembly](https://github.com/sbt/sbt-assembly) which is a dependency of this project. 

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

This plugin uses [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html) 
to retrieve the AWS credentials from your environment.  
It will look for credentials in this order:  
- Environment Variables - AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY
- Java System Properties - aws.accessKeyId and aws.secretKey
- Credential profiles file at the default location (~/.aws/credentials)
 ```
 [default]
aws_access_key_id = your_aws_access_key_id
aws_secret_access_key = your_aws_secret_access_key
 ```
 
- Credentials delivered through the Amazon EC2 container service
- Instance profile credentials delivered through the Amazon EC2 metadata service

Usage
-------------
`sbt deployFunctions` Deploy new AWS Lambda function(s) from the current project. The (fat) jar file will be put to your S3 bucket.

`sbt undeployFunctions` Undeploy existing AWS Lambda function(s) from the current project. The (fat) jar file will be removed from your S3 bucket when all functions are undeployed successfully.


Configuration
-------------

sbt-aws-fn can be configured using sbt settings

| sbt setting         | required?    | Description   |
|:--------------------|:-------------|:--------------|
| awsS3Bucket         | **required** | The name of an S3 bucket where the lambda code will be stored |
| awsRoleArn          | optional     | The [ARN](http://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html "AWS ARN documentation") of an [IAM](https://aws.amazon.com/iam/ "AWS IAM documentation") role to use when creating a new Lambda |
| awsRegion           | optional     | The name of the AWS region to connect to. Defaults to `us-east-1` |
| awsLambdaTimeout    | optional     | The Lambda timeout in seconds (1-300). Defaults to AWS default. |
| awsLambdaMemorySize | optional     |The amount of memory in MB for the Lambda function (128-1536, multiple of 64). Defaults to AWS default. |
| awsLambdaHandlers   | **required** |Sequence of Lambda names to handler functions. |

When you omit `awsRoleArn` setting, the plugin will use the default one which is `lambda_basic_execution`.
If it couldn't find the default one, it will create one for you. This process takes a couple seconds.
Provide one if you want to save a little more time :)

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



Publishing new versions of this plugin
--------------------------------------

This plugin uses [sbt-sonatype](https://github.com/xerial/sbt-sonatype) to publish to TailrecIO's account on maven central

```
sbt publishSigned sonatypeRelease
```
