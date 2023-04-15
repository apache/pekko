/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

import net.vonbuchholtz.sbt.dependencycheck.DependencyCheckPlugin
import sbt.AutoPlugin
import sbt.Keys.baseDirectory
import sbt.Keys._
import sbt._
import net.vonbuchholtz.sbt.dependencycheck.DependencyCheckPlugin.autoImport._

object DependencyCheck extends AutoPlugin {
  override lazy val buildSettings = Seq(
    LocalRootProject / dependencyCheckSuppressionFile := Some(
      baseDirectory.value / "dependency-check" / "suppression.xml"))

  override def requires = plugins.JvmPlugin && DependencyCheckPlugin

  override def trigger = allRequirements

}
