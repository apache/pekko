/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

object PekkoInlinePlugin extends AutoPlugin {
  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = JvmPlugin

  val enabled = !sys.props.contains("pekko.no.inline")

  private val flagsFor212 = Seq(
    "-opt-inline-from:org.apache.pekko.**",
    "-opt-inline-from:<sources>",
    "-opt:l:inline")

  private val flagsFor213 = Seq(
    "-opt-inline-from:org.apache.pekko.**",
    "-opt-inline-from:<sources>",
    "-opt:l:inline")

  // Optimizer not yet available for Scala3, see https://docs.scala-lang.org/overviews/compiler-options/optimizer.html
  private val flagsFor3 = Seq()

  override lazy val projectSettings = Seq(
    Compile / scalacOptions ++= {
      if (enabled) {
        CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, n)) if n == 13 =>
            flagsFor213
          case Some((2, n)) if n == 12 =>
            flagsFor212
          case Some((3, _)) =>
            flagsFor3
        }
      } else Seq.empty
    })
}
