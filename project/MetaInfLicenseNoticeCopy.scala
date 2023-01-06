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
