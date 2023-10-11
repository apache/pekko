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
import Keys._

object ProjectIndexGenerator extends AutoPlugin {

  object CliOptions {
    val generateLicenseReportEnabled = CliOption("pekko.genlicensereport.enabled", true)
  }

  override val projectSettings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators +=
        generateIndex(sourceDirectory, _ / "paradox" / "project" / "index.md")))

  def generateIndex(dir: SettingKey[File], locate: File => File) = Def.task[Seq[File]] {
    val file = locate(dir.value)

    val markdownFilesBeforeLicense = Seq(
      "../common/binary-compatibility-rules.md",
      "scala3.md",
      "downstream-upgrade-strategy.md",
      "../common/may-change.md",
      "../additional/ide.md",
      "immutable.md",
      "migration-guides.md",
      "rolling-update.md",
      "issue-tracking.md",
      "licenses.md"
    )
    val markdownFilesAfterLicense = Seq(
      "../additional/faq.md",
      "../additional/books.md",
      "examples.md",
      "links.md"
    )

     CliOptions.generateLicenseReportEnabled.ifTrue(
      markdownFilesBeforeLicense ++ "license-report.md"
    )

    val content = s"""
                     |# Project Information
                     |
                     |@@toc { depth=2 }
                     |
                     |@@@ index
                     |
                     |${markdownFilesBeforeLicense.map(f => s"* [${f.replace(".md", "")}]($f)").mkString("\n")}
                     |${markdownFilesAfterLicense.map(f => s"* [${f.replace(".md", "")}]($f)").mkString("\n")}
                     |
                     |@@@
                     |
      """.stripMargin

    if (!file.exists || IO.read(file) != content) IO.write(file, content)
    Seq(file)
  }
}
