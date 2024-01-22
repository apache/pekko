/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._

object TestExtras {
  import JdkOptions.isJdk8
  object Filter {
    object Keys {
      lazy val excludeTestNames = settingKey[Set[String]](
        "Names of tests to be excluded. Not supported by MultiJVM tests. Example usage: -Dpekko.test.names.exclude=TimingSpec")
      lazy val excludeTestTags = settingKey[Set[String]](
        "Tags of tests to be excluded. It will not be used if you specify -Dpekko.test.tags.only. Example usage: -Dpekko.test.tags.exclude=long-running")
      lazy val onlyTestTags =
        settingKey[Set[String]]("Tags of tests to be ran. Example usage: -Dpekko.test.tags.only=long-running")

      lazy val checkTestsHaveRun = taskKey[Unit]("Verify a number of notable tests have actually run")
    }

    import Keys._

    private[Filter] object Params {
      lazy val testNamesExclude = systemPropertyAsSeq("pekko.test.names.exclude").toSet
      lazy val testTagsExlcude = systemPropertyAsSeq("pekko.test.tags.exclude").toSet
      lazy val testTagsOnly = systemPropertyAsSeq("pekko.test.tags.only").toSet
    }

    lazy val settings = {
      Seq(
        excludeTestNames := Params.testNamesExclude,
        excludeTestTags := {
          if (onlyTestTags.value.isEmpty) Params.testTagsExlcude
          else Set.empty
        },
        onlyTestTags := Params.testTagsOnly,
        // add filters for tests excluded by name
        Test / testOptions ++= excludeTestNames.value.toSeq.map(exclude =>
          Tests.Filter(test => !test.contains(exclude))),
        // add arguments for tests excluded by tag
        Test / testOptions ++= {
          val tags = excludeTestTags.value
          if (tags.isEmpty) Seq.empty else Seq(Tests.Argument("-l", tags.mkString(" ")))
        },
        // add arguments for running only tests by tag
        Test / testOptions ++= {
          val tags = onlyTestTags.value
          if (tags.isEmpty) Seq.empty else Seq(Tests.Argument("-n", tags.mkString(" ")))
        },
        checkTestsHaveRun := {
          def shouldExist(description: String, filename: String): Unit =
            require(file(filename).exists, s"$description should be run as part of the build")

          val baseList =
            List(
              "The java JavaExtension.java" -> "actor-tests/target/test-reports/TEST-org.apache.pekko.actor.JavaExtension.xml")
          val jdk9Only = List(
            "The jdk9-only FlowPublisherSinkSpec.scala" -> "stream-tests/target/test-reports/TEST-org.apache.pekko.stream.scaladsl.FlowPublisherSinkSpec.xml",
            "The jdk9-only JavaFlowSupportCompileTest.java" -> "stream-tests/target/test-reports/TEST-org.apache.pekko.stream.javadsl.JavaFlowSupportCompileTest.xml")

          val testsToCheck =
            if (isJdk8) baseList
            else baseList ::: jdk9Only

          testsToCheck.foreach((shouldExist _).tupled)
        })
    }

    def containsOrNotExcludesTag(tag: String) = {
      Params.testTagsOnly.contains(tag) || !Params.testTagsExlcude(tag)
    }

    def systemPropertyAsSeq(name: String): Seq[String] = {
      val prop = sys.props.get(name).getOrElse("")
      if (prop.isEmpty) Seq.empty else prop.split(",").toSeq
    }
  }

}
