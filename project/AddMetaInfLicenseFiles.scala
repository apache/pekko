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
// TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
// import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
// import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin.autoImport.*

/**
 * Copies LICENSE and NOTICE files into jar META-INF dir
 */
object AddMetaInfLicenseFiles extends AutoPlugin {

  private lazy val baseDir = LocalRootProject / baseDirectory

  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support — restore ApacheSonatype settings
  // override lazy val projectSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "StandardLicense.txt",
  //   apacheSonatypeNoticeFile := baseDir.value / "legal" / "PekkoNotice.txt")
  override lazy val projectSettings = Seq.empty

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val actorSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-actor-jar-license.txt",
  //   apacheSonatypeNoticeFile := baseDir.value / "legal" / "pekko-actor-jar-notice.txt")
  lazy val actorSettings = Seq.empty

  /**
   * Settings specific for Pekko actor subproject which requires a different license file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val clusterSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-cluster-jar-license.txt")
  lazy val clusterSettings = Seq.empty

  /**
   * Settings specific for Pekko distributed-data subproject which requires a different license file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val distributedDataSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-distributed-data-jar-license.txt")
  lazy val distributedDataSettings = Seq.empty

  /**
   * Settings specific for Pekko persistence-typed subproject which requires a different license file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val persistenceTypedSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-persistence-typed-jar-license.txt")
  lazy val persistenceTypedSettings = Seq.empty

  /**
   * Settings specific for Pekko remote subproject which requires a different license file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val remoteSettings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-remote-jar-license.txt",
  //   apacheSonatypeNoticeFile := baseDir.value / "legal" / "pekko-remote-jar-notice.txt")
  lazy val remoteSettings = Seq.empty

  /**
   * Settings specific for Pekko protobuf-v3 subproject which requires a different license file
   * as well as an additional "COPYING.protobuf" file.
   */
  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // lazy val protobufV3Settings = Seq(
  //   apacheSonatypeLicenseFile := baseDir.value / "legal" / "pekko-protobuf-v3-jar-license.txt") ++ inConfig(Compile)(
  //   Seq(
  //     resourceGenerators += {
  //       Def.task {
  //         List(
  //           ApacheSonatypePlugin.addFileToMetaInf(resourceManaged.value, baseDir.value / "COPYING.protobuf"))
  //       }
  //     }))
  lazy val protobufV3Settings = Seq.empty

  override lazy val trigger = allRequirements

  // TODO [sbt2-migration] Blocked on sbt-pekko-build sbt 2 support
  // override lazy val requires = ApacheSonatypePlugin
  override lazy val requires = plugins.JvmPlugin
}
