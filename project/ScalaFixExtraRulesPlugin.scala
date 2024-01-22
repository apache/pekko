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

object ScalaFixExtraRulesPlugin extends AutoPlugin with ScalafixSupport {
  override lazy val trigger: PluginTrigger = allRequirements

  override lazy val requires: Plugins = ScalafixPlugin

  import sbt._
  import scalafix.sbt.ScalafixPlugin.autoImport.scalafixDependencies
  override lazy val projectSettings: Seq[Def.Setting[_]] = super.projectSettings ++ {
    ThisBuild / scalafixDependencies ++= Seq(
      "com.nequissimus" %% "sort-imports" % "0.6.1",
      // https://github.com/ohze/scala-rewrites
      // an extended version of https://github.com/scala/scala-rewrites
      "com.sandinh" %% "scala-rewrites" % "0.1.10-sd")
  }
}
