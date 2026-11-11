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

import com.github.pjfanning.pekkobuild.{ MiMa => PekkoBuildMiMa }
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._
import sbt._
import sbt.Keys._

/**
 * Checks binary compatibility of the 2.x line against Pekko 2.0.0.
 *
 * Overrides the previous artifacts set by sbt-pekko-build (which targets 1.0.x).
 * MiMa filters for changes made after 2.0.0 go in `src/main/mima-filters/2.1.x.backwards.excludes`
 * (or a later 2.x directory).
 */
object PekkoMiMa extends AutoPlugin {

  val previousVersion = "2.0.0"

  override def requires = PekkoBuildMiMa

  override def trigger = allRequirements

  override val projectSettings = Seq(
    mimaPreviousArtifacts := Set(organization.value %% name.value % previousVersion))
}
