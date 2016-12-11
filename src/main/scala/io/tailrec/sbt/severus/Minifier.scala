package io.tailrec.sbt.severus

import java.io.{BufferedWriter, File, FileWriter}

import io.tailrec.sbt.severus.SeverusModels.FunctionHandlerModel
import proguard.{Configuration, ConfigurationParser, ProGuard}

import scala.util.Try

object Minifier {

  private val defaultConfig = Seq(
    "-dontobfuscate",
    "-dontwarn",
    "-dontnote",
    "-dontoptimize"
  )

  case class SourceJar(inputFile: File) {
    lazy val outputFile: File = {
      val parent = inputFile.getParent
      val outputName = inputFile.getName.stripSuffix(".jar") + ".min.jar"
      new File(parent + File.separator + outputName)
    }
  }

  def writeConfig(fileName: String,
                  srcJar: SourceJar,
                  handlers: Seq[FunctionHandlerModel],
                  userConfig: Seq[String] = Nil): Try[File] = {

    var writer: BufferedWriter = null

    val result = Try {
      writer = new BufferedWriter(new FileWriter(fileName))
      writer.newLine
      writer.append("#").append(fileName)
      writer.newLine
      val inJar  = srcJar.inputFile.getAbsolutePath
      val outJar = srcJar.outputFile.getAbsolutePath
      writer.append("-injars \"").append(inJar).append("\"")
      writer.newLine
      writer.append("-outjars \"").append(outJar).append("\"")
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
      srcJar.outputFile
    }

    Try(Option(writer).map(_.close))

    result
  }

  def minify(fileName: String): Try[Unit] = Try {
    val proConfig = new Configuration()
    val parser = new ConfigurationParser(Array("@"+fileName), System.getProperties())
    Try(parser.parse(proConfig))
    parser.close()
    new ProGuard(proConfig).execute()
  }

}