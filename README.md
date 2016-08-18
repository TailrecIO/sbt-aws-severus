# sbt-aws-fun

[sbt] plugin to manage functions to [AWS Lambda][aws-lambda]

This plugin was inspired by [sbt-aws-lambda]. Thanks to their great work!  
The fat jar is created by [sbt-assembly] which is a dependency of this project.   
You can get started from a template by cloning this repository [sbt-aws-fun-template]

If you use activator, you can create a new project from the activator template like the following:  
`activator new your-project-name aws-lambda-seed`

Installation
------------

Add the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("io.tailrec.sbt" % "sbt-aws-fun" % "1.0.0-SNAPSHOT")
```

Add the `AwsFunPlugin` auto-plugin to your build.sbt:

```scala
enablePlugins(AwsFunPlugin)
```

This plugin uses [DefaultAWSCredentialsProviderChain] to retrieve the AWS credentials from your environment.  
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

sbt-aws-fun can be configured using sbt settings

| sbt setting         | type                  | default value  | description   |
|:--------------------|:----------------------|:---------------|:--------------|
| awsS3Bucket         | Option[String]        | None | The name of an S3 bucket where the lambda code will be stored. |
| awsRoleArn          | Option[String]        | Some("lambda_basic_execution") | The [ARN] of an [IAM] role to use when creating a new Lambda. |
| awsRegion           | Option[String]        | Some("us-east-1") | The name of the AWS region to connect to. |
| awsLambdaTimeout    | Option[Int]           | None | The Lambda timeout in seconds (1-300). Defaults to AWS default. |
| awsLambdaMemorySize | Option[Int]           | None | The amount of memory in MB for the Lambda function (128-1536, multiple of 64). Defaults to AWS default. |
| awsLambdaHandlers   | Seq[(String, String)] | Nil  | Sequence of Lambda names to handler functions. |
| proGuardEnabled     | Boolean               | false | Enabled flag to turn on/off ProGuard optimization. |
| proGuardConfig      | Seq[String]           | Nil | The [ProGuard] configurations. The default configurations are overridden by this value. |  

When you omit `awsRoleArn` setting, the plugin will use the default one which is `lambda_basic_execution`.
If it couldn't find the default one, it will create one for you. This process takes a couple seconds.
Provide one if you want to save a little more time :)

Example configuration:

```scala
retrieveManaged := true

enablePlugins(AwsFunPlugin)

awsLambdaHandlers := Seq(
  "function1"   -> "io.tailrec.example.Lambda::handleRequest1",
  "function2"   -> "io.tailrec.example.Lambda::handleRequest2",
  "function3"   -> "io.tailrec.example.Lambda::handleRequest3"
)

awsS3Bucket := Some("lambda-scala")

awsLambdaMemorySize := Some(192)

awsLambdaTimeout := Some(30)

awsRoleArn := Some("arn:aws:iam::123456789000:role/lambda_basic_execution")

```

Publishing new versions of this plugin
--------------------------------------

This plugin uses [sbt-sonatype] to publish to a maven central

```
sbt publishSigned sonatypeRelease
```

License
-------

[ProGuard] is licensed under the [GNU General Public License][gpl]. sbt and sbt scripts
are included in a [special exception][except] to the GPL licensing.

The code for this sbt plugin is licensed under the [Apache 2.0 License][apache].

[sbt]: https://github.com/sbt/sbt
[aws-lambda]: https://aws.amazon.com/lambda
[sbt-aws-lambda]: https://github.com/gilt/sbt-aws-lambda
[sbt-assembly]: https://github.com/sbt/sbt-assembly
[sbt-aws-fun-template]: https://github.com/TailrecIO/sbt-aws-fun-template
[DefaultAWSCredentialsProviderChain]: http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html
[ARN]: http://docs.aws.amazon.com/general/latest/gr/aws-arns-and-namespaces.html
[IAM]: https://aws.amazon.com/iam/
[sbt-sonatype]: https://github.com/xerial/sbt-sonatype
[ProGuard]: http://proguard.sourceforge.net/
[gpl]: http://www.gnu.org/licenses/gpl.html
[apache]: http://www.apache.org/licenses/LICENSE-2.0.html
[except]: http://proguard.sourceforge.net/GPL_exception.html

