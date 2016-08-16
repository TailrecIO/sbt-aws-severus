
publishMavenStyle := true

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

sonatypeProfileName := "io.tailrec"

pomExtra := {
  <url>https://github.com/TailrecIO/sbt-aws-fn</url>
  <scm>
    <url>git@github.com:TailrecIO/sbt-aws-fn.git</url>
    <connection>scm:git:git@github.com:TailrecIO/sbt-aws-fn.git</connection>
  </scm>
  <licenses>
    <license>
      <name>Apache 2</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
    </license>
  </licenses>
  <developers>
    <developer>
      <id>hussachai</id>
      <name>Hussachai Puripunpinyo</name>
      <url>http://tailrec.io</url>
    </developer>
  </developers>
}
