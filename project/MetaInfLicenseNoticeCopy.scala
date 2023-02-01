/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import sbt.Keys._
import sbt._

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object MetaInfLicenseNoticeCopy {

  private val baseDir = (LocalRootProject / baseDirectory)
  private val standardLicenseFile = Def.task[File] (baseDir.value / "legal" / "StandardLicense.txt")
  private val protobufApacheLicenseFile = Def.task[File] (baseDir.value / "LICENSE")
  private val protobufGoogleLicenseFile = Def.task[File] (baseDir.value / "COPYING.protobuf")
  private val noticeFile = Def.task[File] (baseDir.value / "NOTICE")

  val settings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += copyFileToMetaInf(resourceManaged, standardLicenseFile, "LICENSE"),
      resourceGenerators += copyFileToMetaInf(resourceManaged, noticeFile, "NOTICE")))

  val protobufSettings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += copyFileToMetaInf(resourceManaged, protobufApacheLicenseFile, "LICENSE"),
      resourceGenerators += copyFileToMetaInf(resourceManaged, protobufGoogleLicenseFile, "COPYING.protobuf"),
      resourceGenerators += copyFileToMetaInf(resourceManaged, noticeFile, "NOTICE")))

  private def copyFileToMetaInf(dir: SettingKey[File], fromFile: Def.Initialize[Task[File]],
                                fileName: String) = Def.task[Seq[File]] {
    val toFile = resourceManaged.value / "META-INF" / fileName
    IO.copyFile(fromFile.value, toFile)
    Seq(toFile)
  }

}
