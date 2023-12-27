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

import sbt._
import sbt.Keys._
import sbtdynver.DynVerPlugin.autoImport.previousStableVersion
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

object MiMa extends AutoPlugin {

  override def requires = MimaPlugin
  override def trigger = allRequirements

  val checkMimaFilterDirectories =
    taskKey[Unit]("Check that the mima directories are correct compared to latest version")

  override val projectSettings = Seq(
    mimaReportSignatureProblems := true,
    mimaPreviousArtifacts := {
      previousStableVersion.value match {
        case Some(previousStableVersion) =>
          val Some((currentMajor, currentMinor)) = CrossVersion.partialVersion(version.value)
          val Some((previousStableMajor, _)) = CrossVersion.partialVersion(previousStableVersion)
          // If we bump up the major then lets assume this intentionally breaks binary compatibility
          // so lets not check any artifacts
          // See https://github.com/sbt/sbt-dynver/issues/70#issuecomment-458620722
          if (currentMajor == previousStableMajor + 1 && currentMinor == 0)
            Set.empty
          else
            Set(organization.value %% moduleName.value % previousStableVersion)
        case None => Set.empty
      }
    },
    checkMimaFilterDirectories := checkFilterDirectories(baseDirectory.value, version.value))

  def checkFilterDirectories(moduleRoot: File, version: String): Unit = {
    val strippedPatchRegex = """(\d+)(.*)""".r
    val Some((major, minor)) = CrossVersion.partialVersion(version)

    version match {
      case strippedPatchRegex(_, v) =>
        val patchVersion = v.toInt
        val nextVersionFilterDir =
          moduleRoot / "src" / "main" / "mima-filters" / s"$major.$minor.${patchVersion + 1}.backwards.excludes"
        if (nextVersionFilterDir.exists()) {
          throw new IllegalArgumentException(s"Incorrect mima filter directory exists: '$nextVersionFilterDir' " +
            s"should be with number from current release '${moduleRoot / "src" / "main" / "mima-filters" / s"1.0.$patchVersion.backwards.excludes"}")
        }
    }
  }
}
