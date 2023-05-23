/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import sbt._
import sbt.Keys._
import org.mdedetrich.apache.sonatype.ApacheSonatypePlugin
import sbtdynver.DynVerPlugin
import sbtdynver.DynVerPlugin.autoImport.dynverSonatypeSnapshots

object Publish extends AutoPlugin {

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    startYear := Some(2022),
    developers := List(
      Developer(
        "pekko-contributors",
        "Apache Pekko Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/incubator-pekko/graphs/contributors"))))

  override lazy val buildSettings = Seq(
    dynverSonatypeSnapshots := true)

  override def requires = ApacheSonatypePlugin && DynVerPlugin
}

/**
 * For projects that are not to be published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings =
    Seq(publish / skip := true, Compile / doc / sources := Seq.empty)
}
