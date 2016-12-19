package io.tailrec.sbt.severus

import java.io.{BufferedWriter, File, FileWriter}

import io.tailrec.sbt.severus.config.ConfigModels.{FunctionHandlerModel, LambdaArtifact}
import proguard.{Configuration, ConfigurationParser, ProGuard}
import Resources._

/**
  * @author Hussachai Puripunpinyo
  */
object ArtifactMinifier {

  private val defaultConfig = Seq(
    "-dontobfuscate",
    "-dontwarn",
    "-dontnote",
    "-dontoptimize",
    "-dontpreverify"
  )

  private def writeConfig(artifact: LambdaArtifact,
                  handlers: Seq[FunctionHandlerModel],
                  userConfig: Seq[String] = Nil): File = {

    val configFile = new File(artifact.jarFile.getParent + File.separator + artifact.artifactName + ".pro")

    println(s"Writing Progard configuration at: ${configFile}")

    using(new BufferedWriter(new FileWriter(configFile))) { writer =>

      writer.newLine
      writer.append("#").append(configFile.getAbsolutePath)
      writer.newLine
      writer.append("-injars \"").append(artifact.jarFile.getAbsolutePath).append("\"")
      writer.newLine
      writer.append("-outjars \"").append(artifact.artifactFile.getAbsolutePath).append("\"")
      writer.newLine
      (if (userConfig.nonEmpty) userConfig else defaultConfig).foreach { l =>
        writer.append(l)
        writer.newLine
      }
      writer.newLine
      handlers.groupBy(_.className).foreach { case (className, calls) =>
        writer.append("-keep class ").append(className).append(" {")
        writer.newLine
        calls.foreach { case handler =>
          writer.append("  public ").append(handler.returnType)
            .append(" ").append(handler.methodName).append("(...);")
          writer.newLine
        }
        writer.append("}")
        writer.newLine
      }
      configFile
    }
  }

  def minify(artifact: LambdaArtifact,
             handlers: Seq[FunctionHandlerModel]): File = {

    println(s"Minifying artifact: ${artifact.artifactName}")

    val configFile = writeConfig(artifact, handlers)

    val proConfig = new Configuration()
    val parser = new ConfigurationParser(Array("@"+configFile.getAbsolutePath), System.getProperties())

    try {
      parser.parse(proConfig)
    } finally {
      parser.close()
    }

    new ProGuard(proConfig).execute()

    if(artifact.artifactFile.exists()) artifact.artifactFile
    else sys.error(s"Could not create artifact file: ${artifact.artifactFile}")
  }

}
