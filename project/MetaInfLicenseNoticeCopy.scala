/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object MetaInfLicenseNoticeCopy {

  val settings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += copyFileToMetaInf(resourceManaged, "LICENSE"),
      resourceGenerators += copyFileToMetaInf(resourceManaged, "NOTICE")))

  def copyFileToMetaInf(dir: SettingKey[File], fileName: String) = Def.task[Seq[File]] {
    val fromFile = (LocalRootProject / baseDirectory).value / fileName
    val toFile = resourceManaged.value / "META-INF" / fileName
    IO.copyFile(fromFile, toFile)
    Seq(toFile)
  }

}
