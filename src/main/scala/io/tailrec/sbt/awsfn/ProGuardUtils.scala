package io.tailrec.sbt.awsfn

import java.io.{BufferedWriter, File, FileWriter}

import proguard.{Configuration, ConfigurationParser, ProGuard}

import scala.util.Try

object ProGuardUtils {

  private val defaultConfig = Seq(
    "-dontobfuscate",
    "-dontwarn",
    "-dontoptimize"
  )

  case class InputOutput(input: File, outputName: String) {
    lazy val output: File = {
      val parent = input.getParent
      val outJarName = if(outputName.endsWith(".jar")) outputName else outputName + ".jar"
      new File(parent + File.separator + outJarName)
    }
  }



//  def writeConfig(fileName: String,
//                  fio: InputOutput,
//                  callDefs: Seq[CallDefinition],
//                  userConfig: Seq[String] = Nil) = {
//
//    var writer: BufferedWriter = null
//    try {
//      writer = new BufferedWriter(new FileWriter(fileName))
//      writer.newLine
//      writer.append("#").append(fileName)
//      writer.newLine
//      val inJar  = fio.input.getAbsolutePath
//      val outJar = fio.output.getAbsolutePath
//      writer.append("-injars \"").append(inJar).append("\"")
//      writer.newLine
//      writer.append("-outjars \"").append(outJar).append("\"")
//      writer.newLine
//      (if (userConfig.nonEmpty) userConfig else defaultConfig).foreach { l =>
//        writer.append(l)
//        writer.newLine
//      }
//      writer.newLine
//      callDefs.groupBy(_.className).foreach { case (className, calls) =>
//        writer.append("-keep ").append(className).append(" {")
//        writer.newLine
//        calls.foreach { case c =>
//          writer.append("  public ").append(c.returnType).append(" ").append(c.methodName).append("(...);")
//          writer.newLine
//        }
//        writer.append("}")
//        writer.newLine
//      }
//    } finally {
//      Try(Option(writer).map(_.close))
//    }
//  }

  def run(fileName: String): Unit = Try {
    val proConfig = new Configuration()
    val parser = new ConfigurationParser(Array(fileName), System.getProperties())
    Try(parser.parse(proConfig))
    parser.close()
    new ProGuard(proConfig).execute()
  }.recover {
    case e => sys.error(s"ProGuard Error: ${e.getMessage}")
  }


}
