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
import com.lightbend.sbt.publishrsync.PublishRsyncPlugin.autoImport.publishRsyncHost
import xerial.sbt.Sonatype.autoImport._

object Publish extends AutoPlugin {

  override def trigger = allRequirements

  private val apacheBaseRepo = "repository.apache.org"

  override lazy val projectSettings = Seq(
    publishRsyncHost := "akkarepo@gustav.akka.io",
    credentials ++= apacheNexusCredentials,
    organizationName := "Apache Software Foundation",
    organizationHomepage := Some(url("https://www.apache.org")),
    sonatypeCredentialHost := apacheBaseRepo,
    sonatypeProfileName := "org.apache.pekko",
    startYear := Some(2022),
    developers := List(
      Developer(
        "pekko-contributors",
        "Apache Pekko Contributors",
        "dev@pekko.apache.org",
        url("https://github.com/apache/incubator-pekko/graphs/contributors"))),
    publishMavenStyle := true,
    pomIncludeRepository := (_ => false))

  private def apacheNexusCredentials: Seq[Credentials] =
    (sys.env.get("NEXUS_USER"), sys.env.get("NEXUS_PW")) match {
      case (Some(user), Some(password)) =>
        Seq(Credentials("Apache Nexus Repository Manager", apacheBaseRepo, user, password))
      case _ =>
        Seq.empty
    }
}

/**
 * For projects that are not to be published.
 */
object NoPublish extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings =
    Seq(publish / skip := true, Compile / doc / sources := Seq.empty)
}
