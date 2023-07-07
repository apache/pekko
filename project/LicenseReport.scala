/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
