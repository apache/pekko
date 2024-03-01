/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import java.io.File

import sbt._
import sbt.librarymanagement.SemanticSelector
import sbt.librarymanagement.VersionNumber

object JdkOptions extends AutoPlugin {
  object autoImport {
    lazy val jdk8home = settingKey[String]("JDK 8 home. Only needs to be set when it cannot be auto-detected by sbt")
    lazy val targetSystemJdk = settingKey[Boolean](
      "Target the system JDK instead of building against JDK 8. When this is enabled resulting artifacts may not work on JDK 8!")
  }
  import autoImport._

  lazy val specificationVersion: String = sys.props("java.specification.version")

  object JavaVersion {
    val majorVersion: Int = {
      // FIXME replace with Runtime.version() when we no longer support Java 8
      // See Oracle section 1.5.3 at:
      // https://docs.oracle.com/javase/8/docs/technotes/guides/versioning/spec/versioning2.html
      val version = specificationVersion.split('.')
      val majorString =
        if (version(0) == "1") version(1) // Java 8 will be 1.8
        else version(0) // later will be 9, 10, 11 etc
      majorString.toInt
    }
  }

  lazy val isJdk8: Boolean =
    VersionNumber(specificationVersion).matchesSemVer(SemanticSelector(s"=1.8"))
  lazy val isJdk11orHigher: Boolean =
    VersionNumber(specificationVersion).matchesSemVer(SemanticSelector(">=11"))
  lazy val isJdk17orHigher: Boolean =
    VersionNumber(specificationVersion).matchesSemVer(SemanticSelector(">=17"))

  lazy val versionSpecificJavaOptions =
    if (isJdk17orHigher) {
      // for aeron
      "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" ::
      // for fork join pool
      "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED" ::
      // for LevelDB
      "--add-opens=java.base/java.nio=ALL-UNNAMED" :: Nil
    } else Nil

  def notOnJdk8[T](values: Seq[T]): Seq[T] = if (isJdk8) Seq.empty[T] else values

  def targetJdkScalacOptions(
      targetSystemJdk: Boolean,
      jdk8home: Option[File],
      fullJavaHomes: Map[String, File],
      scalaVersion: String): Seq[String] =
    selectOptions(
      targetSystemJdk,
      jdk8home,
      fullJavaHomes,
      Seq(if (scalaVersion.startsWith("3.")) "-Xtarget:8" else "release:8"),
      (java8home: File) => Seq("-release", "8"))
  def targetJdkJavacOptions(
      targetSystemJdk: Boolean,
      jdk8home: Option[File],
      fullJavaHomes: Map[String, File]): Seq[String] =
    selectOptions(
      targetSystemJdk,
      jdk8home,
      fullJavaHomes,
      Nil,
      // '-release 8' would be a neater option here, but is currently not an
      // option because it doesn't provide access to `sun.misc.Unsafe` https://github.com/akka/akka/issues/27079
      (java8home: File) => Seq("-source", "8", "-target", "8", "-bootclasspath", java8home + "/jre/lib/rt.jar"))

  private def selectOptions(
      targetSystemJdk: Boolean,
      jdk8home: Option[File],
      fullJavaHomes: Map[String, File],
      jdk8options: Seq[String],
      jdk11options: File => Seq[String]): Seq[String] =
    if (targetSystemJdk)
      Nil
    else if (isJdk8)
      jdk8options
    else
      jdk8home.orElse(fullJavaHomes.get("8")) match {
        case Some(java8home) =>
          jdk11options(java8home)
        case None =>
          throw new MessageOnlyException(
            "A JDK 8 installation was not found, but is required to build Apache Pekko. To manually specify a JDK 8 installation, set the JAVA_8_HOME environment variable to its path or use the \"set every jdk8home := \\\"/path/to/jdk\\\" sbt command. If you have no JDK 8 installation, target your system JDK with the \"set every targetSystemJdk := true\" sbt command, but beware resulting artifacts will not work on JDK 8")
      }

  lazy val targetJdkSettings = Seq(targetSystemJdk := false, jdk8home := sys.env.get("JAVA_8_HOME").getOrElse(""))
}
