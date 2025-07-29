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

  lazy val specificationVersion: String = sys.props("java.specification.version")

  object JavaVersion {
    val majorVersion: Int = java.lang.Runtime.version().feature()
  }

  lazy val versionSpecificJavaOptions =
    // for virtual threads
    "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED" ::
    "--add-opens=java.base/java.lang=ALL-UNNAMED" ::
    // for aeron
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED" ::
    // for LevelDB
    "--add-opens=java.base/java.nio=ALL-UNNAMED" :: Nil

  def targetJdkScalacOptions(scalaVersion: String): Seq[String] =
    Seq(if (scalaVersion.startsWith("3.")) "-Xtarget:17" else "release:17")

  val targetJdkJavacOptions = Seq("-source", "17", "-target", "17")
}
