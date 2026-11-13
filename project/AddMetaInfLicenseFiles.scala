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

import sbt.Keys.*
import sbt.*

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object AddMetaInfLicenseFiles extends AutoPlugin {

  object autoImport {
    lazy val checkPackageBinMetaInfLegalFiles =
      taskKey[Unit]("Checks that packaged jars contain META-INF/LICENSE and META-INF/NOTICE files.")
  }

  import autoImport.*

  private val licenseFile = settingKey[File]("The project's packaged license file.")
  private val noticeFile = settingKey[File]("The project's packaged notice file.")
  private def addFileToMetaInf(target: File, source: File): File = {
    val destination = target / "META-INF" / source.getName
    IO.copyFile(source, destination)
    destination
  }

  private lazy val baseDir = LocalRootProject / baseDirectory

  private val requiredPackageBinEntries = Seq("META-INF/LICENSE", "META-INF/NOTICE")

  override lazy val projectSettings = Seq(
    licenseFile := baseDir.value / "legal" / "StandardLicense.txt",
    noticeFile := baseDir.value / "legal" / "PekkoNotice.txt",
    Compile / resourceGenerators += Def.task {
      Def.uncached {
        val root = (Compile / resourceManaged).value / "META-INF"
        val license = root / "LICENSE"
        val notice = root / "NOTICE"
        IO.copyFile(licenseFile.value, license)
        IO.copyFile(noticeFile.value, notice)
        Seq(license, notice)
      }
    },
    Compile / checkPackageBinMetaInfLegalFiles := Def.taskDyn {
      if ((publish / skip).value) {
        Def.task {
          streams.value.log.debug("Skipping META-INF legal files check for non-published project.")
        }
      } else {
        Def.task {
          val converter = fileConverter.value
          val packageBinJar = converter.toPath((Compile / packageBin).value).toFile
          val publishedJar = converter.toPath((Compile / packageBin / packagedArtifact).value._2).toFile
          val missingEntries = Seq(packageBinJar, publishedJar).distinct.flatMap { jar =>
            missingPackageBinEntries(jar).map(entry => s"${jar.getAbsolutePath}: $entry")
          }

          if (missingEntries.nonEmpty) {
            throw new MessageOnlyException(
              "Packaged jars are missing required META-INF legal files:\n" + missingEntries.mkString("\n"))
          }
        }
      }
    }.value)

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  lazy val actorSettings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-actor-jar-license.txt",
    noticeFile := baseDir.value / "legal" / "pekko-actor-jar-notice.txt")

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  lazy val clusterSettings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-cluster-jar-license.txt")

  /**
   * Settings specific for Pekko distributed-data subproject which requires a different license file.
   */
  lazy val distributedDataSettings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-distributed-data-jar-license.txt")

  /**
   * Settings specific for Pekko persistence-typed subproject which requires a different license file.
   */
  lazy val persistenceTypedSettings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-persistence-typed-jar-license.txt")

  /**
   * Settings specific for Pekko remote subproject which requires a different license file.
   */
  lazy val remoteSettings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-remote-jar-license.txt",
    noticeFile := baseDir.value / "legal" / "pekko-remote-jar-notice.txt")

  /**
   * Settings specific for Pekko protobuf-v3 subproject which requires a different license file
   * as well as an additional "COPYING.protobuf" file.
   */
  lazy val protobufV3Settings = Seq(
    licenseFile := baseDir.value / "legal" / "pekko-protobuf-v3-jar-license.txt") ++ inConfig(Compile)(
    Seq(
      resourceGenerators += {
        Def.task {
          Def.uncached {
            Seq(
              addFileToMetaInf(resourceManaged.value, baseDir.value / "COPYING.protobuf"))
          }
        }
      }))

  override lazy val trigger = allRequirements

  override lazy val requires = plugins.JvmPlugin

  private def missingPackageBinEntries(jar: File): Seq[String] = {
    val zip = new java.util.zip.ZipFile(jar)
    try requiredPackageBinEntries.filter(entry => zip.getEntry(entry) == null)
    finally zip.close()
  }
}
