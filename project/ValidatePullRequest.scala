/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import com.github.sbt.pullrequestvalidator.ValidatePullRequest
import com.github.sbt.pullrequestvalidator.ValidatePullRequest.PathGlobFilter
import com.lightbend.paradox.sbt.ParadoxPlugin
import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport.paradox
import com.typesafe.tools.mima.plugin.MimaKeys.mimaReportBinaryIssues
import com.typesafe.tools.mima.plugin.MimaPlugin
import sbtunidoc.BaseUnidocPlugin.autoImport.unidoc
import sbt.Keys._
import sbt._

object PekkoValidatePullRequest extends AutoPlugin {

  object CliOptions {
    lazy val mimaEnabled = CliOption("pekko.mima.enabled", true)
  }

  import ValidatePullRequest.autoImport._

  override lazy val trigger = allRequirements
  override lazy val requires = ValidatePullRequest

  lazy val ValidatePR = config("pr-validation").extend(Test)

  override lazy val projectConfigurations = Seq(ValidatePR)

  lazy val additionalTasks = settingKey[Seq[TaskKey[_]]]("Additional tasks for pull request validation")

  override lazy val globalSettings = Seq(credentials ++= {
      // todo this should probably be supplied properly
      GitHub.envTokenOrThrow.map { token =>
        Credentials("GitHub API", "api.github.com", "", token)
      }
    }, additionalTasks := Seq.empty)

  override lazy val buildSettings = Seq(
    validatePullRequest / includeFilter := {
      val ignoredProjects = List(
        "pekko", // This is the root project
        "serialVersionRemoverPlugin")

      loadedBuild.value.allProjectRefs.collect {
        case (_, project) if !ignoredProjects.contains(project.id) =>
          val directory = project.base.getPath.split(java.io.File.separatorChar).last
          PathGlobFilter(s"$directory/**")
      }.fold(FileFilter.nothing)(_ || _)
    },
    validatePullRequestBuildAll / excludeFilter := PathGlobFilter("project/MiMa.scala"),
    prValidatorGithubRepository := Some("apache/pekko"),
    prValidatorTargetBranch := "origin/main")

  override lazy val projectSettings = inConfig(ValidatePR)(Defaults.testTasks) ++ Seq(
    ValidatePR / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "performance"),
    ValidatePR / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "long-running"),
    ValidatePR / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "timing"),
    ValidatePR / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-l", "gh-exclude"),
    // make it fork just like regular test running
    ValidatePR / fork := (Test / fork).value,
    ValidatePR / testGrouping := (Test / testGrouping).value,
    ValidatePR / javaOptions := (Test / javaOptions).value,
    prValidatorTasks := Seq(ValidatePR / test) ++ additionalTasks.value,
    prValidatorEnforcedBuildAllTasks := Seq(Test / test) ++ additionalTasks.value)
}

/**
 * This autoplugin adds Multi Jvm tests to validatePullRequest task.
 * It is needed, because ValidatePullRequest autoplugin does not depend on MultiNode and
 * therefore test:executeTests is not yet modified to include multi-jvm tests when ValidatePullRequest
 * build strategy is being determined.
 *
 * Making ValidatePullRequest depend on MultiNode is impossible, as then ValidatePullRequest
 * autoplugin would trigger only on projects which have both of these plugins enabled.
 */
object MultiNodeWithPrValidation extends AutoPlugin {
  import PekkoValidatePullRequest._

  override lazy val trigger = allRequirements
  override lazy val requires = PekkoValidatePullRequest && MultiNode
  override lazy val projectSettings =
    if (MultiNode.multiNodeTestInTest) Seq(additionalTasks += MultiNode.multiTest)
    else Seq.empty
}

/**
 * This autoplugin adds MiMa binary issue reporting to validatePullRequest task,
 * when a project has MimaPlugin autoplugin enabled.
 */
object MimaWithPrValidation extends AutoPlugin {
  import PekkoValidatePullRequest._

  override lazy val trigger = allRequirements
  override lazy val requires = PekkoValidatePullRequest && MimaPlugin
  override lazy val projectSettings =
    CliOptions.mimaEnabled.ifTrue(additionalTasks += mimaReportBinaryIssues).toList
}

/**
 * This autoplugin adds Paradox doc generation to validatePullRequest task,
 * when a project has ParadoxPlugin autoplugin enabled.
 */
object ParadoxWithPrValidation extends AutoPlugin {
  import PekkoValidatePullRequest._

  override lazy val trigger = allRequirements
  override lazy val requires = PekkoValidatePullRequest && ParadoxPlugin
  override lazy val projectSettings = Seq(additionalTasks += Compile / paradox)
}

object UnidocWithPrValidation extends AutoPlugin {
  import PekkoValidatePullRequest._

  override lazy val trigger = noTrigger
  override lazy val projectSettings = Seq(additionalTasks += Compile / unidoc)
}
