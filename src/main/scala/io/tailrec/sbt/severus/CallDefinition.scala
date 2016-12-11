package io.tailrec.sbt.severus

object CallDefinition extends App {

  def apply(functionName: String, handler: String): CallDefinition = {
    val idx1 = handler.indexOf("::")
    val idx2 = handler.indexOf("#")
    require(idx1 != -1, "Handler must have '::' between fully qualified class name and function name")
    val className = handler.substring(0, idx1)
    val (methodName, returnType)  = if(idx2 != -1) {
      val t = handler.substring(idx2 + 1)
      (handler.substring(idx1 + 2, idx2), if(t == "scala.Unit") "void" else t)
    } else {
      (handler.substring(idx1 + 2), "void")
    }

    CallDefinition(functionName, Handler(className, methodName, returnType))
  }

  println(apply("test", "io.tailrec.example.Lambda::handler1"))
  println(apply("test", "io.tailrec.example.Lambda::handler2#scala.Unit"))
  println(apply("test", "io.tailrec.example.Lambda::handler3#void"))
  println(apply("test", "io.tailrec.example.Lambda::handler4#java.lang.String").handler.toAWSLambdaHandler)
}

case class Handler(className: String, methodName: String, returnType: String) {
  /**
    * The handler string in AWS Lambda format
    */
  lazy val toAWSLambdaHandler: String = className + "::" + methodName
}

case class CallDefinition(functionName: String, handler: Handler)


