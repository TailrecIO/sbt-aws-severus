package io.tailrec.sbt.severus.config

/**
  * @author Hussachai Puripunpinyo
  */
object ConfigValidators {

  private val FileNamePattern = "^[\\w-][\\w.-]*[\\w]$".r.pattern

  object Lambda {

    def validateFunctionName(name: String): Unit = {
      require(FileNamePattern.matcher(name).matches(),
        s"Function($name) -> name must conform to: ${FileNamePattern.pattern()}")
    }

    def validateMemory(functionName: String, memory: Int): Unit = {
      require(memory >= 128 && memory <= 1536 && (memory % 64 == 0),
        s"Function($functionName) -> memory must be between 128 and 1536, and it must be multiple of 64")
    }

    def validateTimeout(functionName: String, timeout: Int): Unit = {
      require(timeout > 0 && timeout <= 300,
        s"Function($functionName) -> timeout must be between 1 and 300 seconds")
    }

  }
}
