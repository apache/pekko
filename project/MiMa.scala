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

import scala.collection.immutable
import sbt._
import sbt.Keys._
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

object MiMa extends AutoPlugin {

  private val latestPatchOf10 = 0

  override def requires = MimaPlugin
  override def trigger = allRequirements

  val checkMimaFilterDirectories =
    taskKey[Unit]("Check that the mima directories are correct compared to latest version")

  override val projectSettings = Seq(
    mimaReportSignatureProblems := true,
    mimaPreviousArtifacts := pekkoPreviousArtifacts(name.value, organization.value, scalaBinaryVersion.value),
    checkMimaFilterDirectories := checkFilterDirectories(baseDirectory.value))

  def checkFilterDirectories(moduleRoot: File): Unit = {
    val nextVersionFilterDir =
      moduleRoot / "src" / "main" / "mima-filters" / s"1.0.${latestPatchOf10 + 1}.backwards.excludes"
    if (nextVersionFilterDir.exists()) {
      throw new IllegalArgumentException(s"Incorrect mima filter directory exists: '$nextVersionFilterDir' " +
        s"should be with number from current release '${moduleRoot / "src" / "main" / "mima-filters" / s"1.0.$latestPatchOf10.backwards.excludes"}")
    }
  }

  def pekkoPreviousArtifacts(
      projectName: String,
      organization: String,
      scalaBinaryVersion: String): Set[sbt.ModuleID] = {
    if (scalaBinaryVersion.startsWith("3")) {
      // No binary compatibility for 3.0 artifacts for now - experimental
      Set.empty
    } else {
      val versions: Seq[String] = {
        val firstPatchOf10 = 0

        val pekko10Previous = expandVersions(1, 0, 0 to latestPatchOf10)

        pekko10Previous
      }

      // check against all binary compatible artifacts
      versions.map { v =>
        organization %% projectName % v
      }.toSet
    }
  }

  private def expandVersions(major: Int, minor: Int, patches: immutable.Seq[Int]): immutable.Seq[String] =
    patches.map(patch => s"$major.$minor.$patch")
}
