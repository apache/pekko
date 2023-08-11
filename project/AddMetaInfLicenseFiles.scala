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

import sbt.Keys._
import sbt._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin.autoImport._

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object AddMetaInfLicenseFiles extends AutoPlugin {

  private lazy val baseDir = LocalRootProject / baseDirectory

  override lazy val projectSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "StandardLicense.txt",
    apacheSonatypeNoticeFile := baseDir.value / "legal" / "PekkoNotice.txt",
    apacheSonatypeDisclaimerFile := Some(baseDir.value / "DISCLAIMER"))

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  lazy val actorSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-actor-jar-license.txt",
    apacheSonatypeNoticeFile := baseDir.value / "legal" / "pekko-actor-jar-notice.txt")

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  lazy val clusterSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-cluster-jar-license.txt")

  /**
   * Settings specific for Pekko distributed-data subproject which requires a different license file.
   */
  lazy val distributedDataSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-distributed-data-jar-license.txt")

  /**
   * Settings specific for Pekko persistence-typed subproject which requires a different license file.
   */
  lazy val persistenceTypedSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-persistence-typed-jar-license.txt")

  /**
   * Settings specific for Pekko remote subproject which requires a different license file.
   */
  lazy val remoteSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-remote-jar-license.txt",
    apacheSonatypeNoticeFile := baseDir.value / "legal" / "pekko-remote-jar-notice.txt")

  /**
   * Settings specific for Pekko stream subproject which requires a different license file.
   */
  lazy val streamSettings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-stream-jar-license.txt")

  /**
   * Settings specific for Pekko protobuf-v3 subproject which requires a different license file
   * as well as an additional "COPYING.protobuf" file.
   */
  lazy val protobufV3Settings = Seq(
    apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-protobuf-v3-jar-license.txt") ++ inConfig(Compile)(
    Seq(
      resourceGenerators += {
        Def.task {
          List(
            ApacheSonatypePlugin.addFileToMetaInf(resourceManaged.value, baseDir.value / "COPYING.protobuf"))
        }
      }))

  override def trigger = allRequirements

  override def requires = ApacheSonatypePlugin
}
