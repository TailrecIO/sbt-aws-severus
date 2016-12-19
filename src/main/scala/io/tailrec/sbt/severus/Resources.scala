package io.tailrec.sbt.severus

import java.io.{File, FileInputStream, InputStream}
import java.nio.file.{Files, Paths, StandardOpenOption}
import StandardOpenOption._
import scala.io.{Codec, Source}

/**
  * @author Hussachai Puripunpinyo
  */
object Resources {

  def using[S <: AutoCloseable, T](res : S)(block: S => T): T = {
    try {
      block(res)
    } finally {
      if(res != null) res.close
    }
  }

  def readFile[T](file: File)(block: InputStream => T): T = {
    using(new FileInputStream(file))(in => block(in))
  }

  def readFileAsString(file: File, codec: Codec = Codec.UTF8): String = {
    new String(Files.readAllBytes(Paths.get(file.toURI)), codec.charSet)
  }

  def writeStringToFile(file: File, data: String, codec: Codec = Codec.UTF8): Unit = {
    Files.write(Paths.get(file.toURI), data.getBytes(codec.charSet), CREATE, WRITE, TRUNCATE_EXISTING)
  }

  def readResourceAsString(path: String, codec: Codec = Codec.UTF8): String = {
    readResource(path){ in =>
      Source.fromInputStream(in)(codec).mkString
    }
  }

  def readResource[T](path: String)(block: InputStream => T): T = {
    using {
      if(path.startsWith("classpath:")) {
        getClass.getClassLoader.getResourceAsStream(path.stripPrefix("classpath:"))
      } else {
        new FileInputStream(new File(path.stripPrefix("file://")))
      }
    }(in => block(in))
  }
}
