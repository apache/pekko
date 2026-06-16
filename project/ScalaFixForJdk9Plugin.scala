/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.{ AutoPlugin, PluginTrigger, Plugins }
import scalafix.sbt.ScalafixPlugin

object ScalaFixForJdk9Plugin extends AutoPlugin with ScalafixSupport {
  override lazy val trigger: PluginTrigger = allRequirements
  import Jdk9._
  override lazy val requires: Plugins = Jdk9 && ScalafixPlugin

  import ScalafixPlugin.autoImport.scalafixConfigSettings
  import sbt._

  lazy val scalafixIgnoredSetting: Seq[Setting[_]] = Seq(ignore(TestJdk9))

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    Seq(CompileJdk9, TestJdk9).flatMap(c => inConfig(c)(scalafixConfigSettings(c))) ++
    scalafixIgnoredSetting ++ Seq(
      updateProjectCommands(
        alias = "fixall",
        value = ";scalafixEnable;scalafixAll;scalafmtAll;test:compile;multi-jvm:compile;reload"),
      updateProjectCommands(alias = "sortImports", value = ";scalafixEnable;scalafixAll SortImports;scalafmtAll"))
}
