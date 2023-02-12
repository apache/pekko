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
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin
import org.mdedetrich.apache.sonatype.SonatypeApachePlugin.autoImport._

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object AddMetaInfLicenseFiles extends AutoPlugin {

  private lazy val baseDir = LocalRootProject / baseDirectory

  override lazy val projectSettings = Seq(
    apacheSonatypeDisclaimerFile := Some(baseDir.value / "DISCLAIMER"))

  /**
   * Settings specific for Pekko protobuf subprojects which require a different license file
   * as well as an additional "COPYING.protobuf" file.
   */
  lazy val protobufSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "LICENSE.protobuf") ++ inConfig(Compile)(Seq(
    resourceGenerators += {
      Def.task {
        List(
          SonatypeApachePlugin.addFileToMetaInf(resourceManaged.value, baseDir.value / "legal" / "COPYING.protobuf"))
      }
    }))

  override def trigger = allRequirements

  override def requires = SonatypeApachePlugin
}
