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

object ScalaFixForJdk21Plugin extends AutoPlugin with ScalafixSupport {
  override lazy val trigger: PluginTrigger = allRequirements
  import Jdk21._
  override lazy val requires: Plugins = Jdk21 && ScalafixPlugin

  import ScalafixPlugin.autoImport.scalafixConfigSettings
  import sbt._

  lazy val scalafixIgnoredSetting: Seq[Setting[?]] = Seq(ignore(TestJdk21))

  override lazy val projectSettings: Seq[Def.Setting[?]] =
    Seq(CompileJdk21, TestJdk21).flatMap(c => inConfig(c)(scalafixConfigSettings(c))) ++
    scalafixIgnoredSetting ++ Seq(
      updateProjectCommands(
        alias = "fixall",
        value = ";scalafixEnable;scalafixAll;scalafmtAll;test:compile;multi-jvm:compile;reload"),
      updateProjectCommands(alias = "sortImports", value = ";scalafixEnable;scalafixAll OrganizeImports;scalafmtAll"))
}
