/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.{ inConfig, AutoPlugin, Def, PluginTrigger, Plugins, Setting }
import scalafix.sbt.ScalafixPlugin
import scalafix.sbt.ScalafixPlugin.autoImport.scalafixConfigSettings

object ScalafixForMultiNodePlugin extends AutoPlugin with ScalafixSupport {
  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = MultiNode && ScalafixPlugin

  import MultiJvmPlugin.autoImport._

  lazy val scalafixIgnoredSetting: Seq[Setting[_]] = Seq(ignore(MultiJvm))

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(MultiJvm).flatMap(c => inConfig(c)(scalafixConfigSettings(c))) ++
    scalafixIgnoredSetting ++ Seq(
      updateProjectCommands(alias = "fixall", value = ";scalafixEnable;scalafixAll;scalafmtAll"),
      updateProjectCommands(alias = "sortImports", value = ";scalafixEnable;scalafixAll SortImports;scalafmtAll"))
}
