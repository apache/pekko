/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import sbt._
import sbtlicensereport.SbtLicenseReport
import sbtlicensereport.SbtLicenseReport.autoImportImpl._
import sbtlicensereport.license.{ DepModuleInfo, MarkDown }

object LicenseReport extends AutoPlugin {

  override lazy val projectSettings = Seq(
    licenseReportTypes := Seq(MarkDown),
    licenseReportMakeHeader := (language => language.header1("License Report")),
    licenseConfigurations := Set("compile", "test", "provided"),
    licenseDepExclusions := {
      case DepModuleInfo("org.apache.pekko", _, _) => true // Inter pekko project dependencies are pointless
      case DepModuleInfo(_, "scala-library", _)    => true // Scala library is part of Scala language
    },
    licenseReportColumns := Seq(
      Column.Category,
      Column.License,
      Column.Dependency,
      Column.OriginatingArtifactName,
      Column.Configuration))

  override def requires = plugins.JvmPlugin && SbtLicenseReport

  override def trigger = allRequirements

}
