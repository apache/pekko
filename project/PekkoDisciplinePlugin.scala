/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import sbt._
import Keys.{ scalacOptions, _ }
import sbt.plugins.JvmPlugin

object PekkoDisciplinePlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements
  override def requires: Plugins = JvmPlugin
  override lazy val projectSettings = disciplineSettings

  // allow toggling for pocs/exploration of ideas without discipline
  val enabled = !sys.props.contains("pekko.no.discipline")

  // We allow warnings in docs to get the 'snippets' right
  val nonFatalJavaWarningsFor = Set(
    // for sun.misc.Unsafe and AbstractScheduler
    "pekko-actor",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-actor-typed-tests",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-cluster-typed",
    // use of deprecated org.apache.pekko.protobuf.GeneratedMessage
    "pekko-protobuf",
    "pekko-protobuf-v3",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-remote",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-distributed-data",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-cluster-sharding-typed",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-persistence-typed",
    // references to deprecated PARSER fields in generated message formats?
    "pekko-persistence-query",
    "pekko-docs",
    // references to deprecated Jackson methods that would involve a significant refactor to avoid
    "pekko-serialization-jackson",
    // use varargs of `Graph` in alsoTo and etc operators
    "pekko-stream-tests")

  val looseProjects = Set(
    "pekko-actor",
    "pekko-actor-testkit-typed",
    "pekko-actor-tests",
    "pekko-actor-typed",
    "pekko-actor-typed-tests",
    "pekko-bench-jmh",
    "pekko-cluster",
    "pekko-cluster-metrics",
    "pekko-cluster-sharding",
    "pekko-cluster-sharding-typed",
    "pekko-distributed-data",
    "pekko-docs",
    "pekko-persistence",
    "pekko-persistence-tck",
    "pekko-persistence-typed",
    "pekko-persistence-query",
    "pekko-remote",
    "pekko-remote-tests",
    "pekko-stream",
    "pekko-stream-testkit",
    "pekko-stream-tests",
    "pekko-stream-tests-tck",
    "pekko-testkit")

  val defaultScalaOptions = "-Wconf:cat=unused-nowarn:s,any:e"

  lazy val nowarnSettings = Seq(
    Compile / scalacOptions ++= (
      if (scalaVersion.value.startsWith("3.")) Nil
      else Seq(defaultScalaOptions)
    ),
    Test / scalacOptions ++= (
      if (scalaVersion.value.startsWith("3.")) Nil
      else Seq(defaultScalaOptions)
    ),
    Compile / doc / scalacOptions := Seq())

  /**
   * We are a little less strict in docs
   */
  val docs =
    Seq(
      Compile / scalacOptions -= defaultScalaOptions,
      Compile / scalacOptions ++= (
        if (scalaVersion.value.startsWith("3.")) Nil
        else Seq("-Wconf:cat=unused:s,cat=deprecation:s,cat=unchecked:s,any:e")
      ),
      Test / scalacOptions --= Seq("-Xlint", "-unchecked", "-deprecation"),
      Test / scalacOptions -= defaultScalaOptions,
      Test / scalacOptions ++= (
        if (scalaVersion.value.startsWith("3.")) Nil
        else Seq("-Wconf:cat=unused:s,cat=deprecation:s,cat=unchecked:s,any:e")
      ),
      Compile / doc / scalacOptions := Seq())

  lazy val disciplineSettings =
    if (enabled) {
      nowarnSettings ++ Seq(
        Compile / scalacOptions ++= Seq("-Xfatal-warnings"),
        Test / scalacOptions --= testUndiscipline,
        Compile / javacOptions ++= (
          if (scalaVersion.value.startsWith("3.")) {
            Seq()
          } else {
            if (!nonFatalJavaWarningsFor(name.value)) Seq("-Werror", "-Xlint:deprecation", "-Xlint:unchecked")
            else Seq.empty
          }
        ),
        Compile / doc / javacOptions := Seq("-Xdoclint:none"),
        Compile / scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
          case Some((2, 13)) =>
            disciplineScalacOptions -- Set(
              "-Ywarn-inaccessible",
              "-Ywarn-infer-any",
              "-Ywarn-nullary-override",
              "-Ywarn-nullary-unit",
              "-Ypartial-unification",
              "-Yno-adapted-args") ++ Set(
              "-Xlint:-strict-unsealed-patmat")
          case Some((2, 12)) =>
            disciplineScalacOptions
          case _ =>
            Seq("-Wconf:cat=deprecation:s")
        }).toSeq,
        Compile / scalacOptions --=
          (if (looseProjects.contains(name.value)) undisciplineScalacOptions.toSeq
           else Seq.empty),
        // Discipline is not needed for the docs compilation run (which uses
        // different compiler phases from the regular run), and in particular
        // '-Ywarn-unused:explicits' breaks 'sbt ++2.13.0-M5 actor/doc'
        // https://github.com/akka/akka/issues/26119
        Compile / doc / scalacOptions --= disciplineScalacOptions.toSeq :+ "-Xfatal-warnings",
        // having discipline warnings in console is just an annoyance
        Compile / console / scalacOptions --= disciplineScalacOptions.toSeq,
        Test / console / scalacOptions --= disciplineScalacOptions.toSeq)
    } else {
      // we still need these in opt-out since the annotations are present
      nowarnSettings ++ Seq(Compile / scalacOptions += "-deprecation")
    }

  val testUndiscipline = Seq("-Ywarn-dead-code" // '???' used in compile only specs
  )

  /**
   * Remain visibly filtered for future code quality work and removing.
   */
  val undisciplineScalacOptions = Set("-Ywarn-numeric-widen")

  /** These options are desired, but some are excluded for the time being */
  val disciplineScalacOptions = Set(
    "-Ywarn-numeric-widen",
    "-Yno-adapted-args",
    "-deprecation",
    "-Xlint",
    "-Xlint:-infer-any",
    "-Ywarn-dead-code",
    "-Ywarn-inaccessible",
    "-Ywarn-infer-any",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused:_",
    "-Ypartial-unification",
    "-Ywarn-extra-implicit")

}
