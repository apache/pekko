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

  object JavaVersion {
    val majorVersion: Int = java.lang.Runtime.version().feature()
  }

  val targetJavaVersion = "17"

  lazy val versionSpecificJavaOptions =
    // for Agrona UnsafeApi and virtual threads
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" ::
    // for virtual threads
    "--add-opens=java.base/java.lang=ALL-UNNAMED" ::
    // for aeron
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" ::
    // for LevelDB
    "--add-opens=java.base/java.nio=ALL-UNNAMED" :: Nil

  def isScala3_8Plus(scalaVersion: String): Boolean =
    scalaVersion.startsWith("3.") && {
      val parts = scalaVersion.split('.')
      def safeToInt(s: String): Int = try { s.split('-').head.toInt }
      catch { case _: NumberFormatException => 0 }
      parts.length >= 2 && (safeToInt(parts(1)) >= 8 || safeToInt(parts(0)) > 3)
    }

  def targetJdkScalacOptions(scalaVersion: String): Seq[String] = {
    if (isScala3_8Plus(scalaVersion)) {
      Seq("-java-output-version", JdkOptions.targetJavaVersion)
    } else {
      Seq("-release", JdkOptions.targetJavaVersion) ++ {
        if (scalaVersion.startsWith("3.")) Seq(s"-Xtarget:${targetJavaVersion}") else Seq.empty
      }
    }
  }

  val targetJdkJavacOptions = Seq("--release", targetJavaVersion)
}
