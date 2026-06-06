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

import TestExtras.Filter.Keys.*
// sbt 2: MultiJvmPlugin is now in package sbt, qualify the import
import sbt.MultiJvmPlugin.autoImport.multiJvmCreateLogger
import sbt.MultiJvmPlugin.autoImport.*

import sbt.{ Def, * }
import sbt.Keys.*
import sbtheader.HeaderPlugin.autoImport.*
import org.scalafmt.sbt.ScalafmtPlugin.scalafmtConfigSettings
import sbtassembly.MergeStrategy
import sbtassembly.AssemblyKeys.*

object MultiNode extends AutoPlugin {

  object autoImport {
    lazy val validateCompile = taskKey[Unit]("Validates compile for any project it is enabled")
  }
  import autoImport.*

  // MultiJvm tests can be excluded from normal test target an validatePullRequest
  // with -Dpekko.test.multi-in-test=false
  lazy val multiNodeTestInTest: Boolean = sys.props.getOrElse("pekko.test.multi-in-test", "true").toBoolean

  object CliOptions {
    lazy val multiNode = CliOption("pekko.test.multi-node", false)
    lazy val sbtLogNoFormat = CliOption("sbt.log.noformat", false)

    lazy val hostsFileName = sys.props.get("pekko.test.multi-node.hostsFileName").toSeq
    lazy val javaName = sys.props.get("pekko.test.multi-node.java").toSeq
    lazy val targetDirName = sys.props.get("pekko.test.multi-node.targetDirName").toSeq
  }

  // sbt 2: use if/else instead of ifTrue/getOrElse to avoid Configuration `/` overload ambiguity
  lazy val multiExecuteTests =
    if (CliOptions.multiNode.get) (MultiJvm / multiNodeExecuteTests) else (MultiJvm / executeTests)
  lazy val multiTest =
    if (CliOptions.multiNode.get) (MultiJvm / multiNodeTest) else (MultiJvm / test)

  override lazy val trigger = noTrigger
  override lazy val requires = plugins.JvmPlugin && sbt.MultiJvmPlugin

  override lazy val projectSettings: Seq[Def.Setting[?]] = multiJvmSettings

  private lazy val defaultMultiJvmOptions: Seq[String] = {
    import scala.jdk.CollectionConverters.*
    // multinode.D= and multinode.X= makes it possible to pass arbitrary
    // -D or -X arguments to the forked jvm, e.g.
    // -Dmultinode.Djava.net.preferIPv4Stack=true -Dmultinode.Xmx512m -Dmultinode.XX:MaxPermSize=256M
    // -DMultiJvm.pekko.cluster.Stress.nrOfNodes=15
    val MultinodeJvmArgs = "multinode\\.(D|X)(.*)".r
    val knownPrefix = Set("pekko.", "akka.", "MultiJvm.", "aeron.")
    val pekkoProperties = System.getProperties.stringPropertyNames.asScala.toList.collect {
      case MultinodeJvmArgs(a, b) =>
        val value = System.getProperty("multinode." + a + b)
        "-" + a + b + (if (value == "") "" else "=" + value)
      case key: String if knownPrefix.exists(pre => key.startsWith(pre)) => "-D" + key + "=" + System.getProperty(key)
    }

    "-Xmx256m" :: pekkoProperties ::: CliOptions.sbtLogNoFormat.ifTrue("-Dpekko.test.nocolor=true").toList
  } ++ JdkOptions.versionSpecificJavaOptions

  private lazy val anyConfigsInThisProject = ScopeFilter(configurations = inAnyConfiguration)

  private lazy val multiJvmSettings =
    sbt.MultiJvmPlugin.multiJvmSettings ++
    scalafmtConfigSettings(MultiJvm) ++
    Seq(
      // Hack because 'provided' dependencies by default are not picked up by the multi-jvm plugin:
      MultiJvm / managedClasspath := Def.uncached {
        (MultiJvm / managedClasspath).value ++ (Compile / managedClasspath).value.filter(_.data.name.contains("silencer-lib"))
      },
      MultiJvm / jvmOptions := defaultMultiJvmOptions,
      MultiJvm / scalacOptions := (Test / scalacOptions).value,
      multiJvmCreateLogger / logLevel := Level.Debug, //  to see ssh establishment
      MultiJvm / assembly / assemblyMergeStrategy := {
        case n if n.endsWith("logback-test.xml")                => MergeStrategy.first
        case n if n.endsWith("io.netty.versions.properties")    => MergeStrategy.first
        case n if n.toLowerCase.matches("meta-inf.*\\.default") => MergeStrategy.first
        case n                                                  => (MultiJvm / assembly / assemblyMergeStrategy).value.apply(n)
      },
      MultiJvm / multiJvmCreateLogger := Def.uncached { // to use normal sbt logging infra instead of custom sbt-multijvm-one
        val previous = (MultiJvm / multiJvmCreateLogger).value
        val logger = streams.value.log
        (name: String) =>
          new Logger {
            def trace(t: => Throwable): Unit = { logger.trace(t) }
            def success(message: => String): Unit = { logger.success(message) }
            def log(level: Level.Value, message: => String): Unit =
              logger.log(level, s"[${scala.Console.BLUE}$name${scala.Console.RESET}] $message")
          }
      }) ++
    CliOptions.hostsFileName.map(MultiJvm / multiNodeHostsFileName := _) ++
    CliOptions.javaName.map(MultiJvm / multiNodeJavaName := _) ++
    CliOptions.targetDirName.map(MultiJvm / multiNodeTargetDirName := _) ++
    (if (multiNodeTestInTest) {
       // make sure that MultiJvm tests are executed by the default test target,
       // and combine the results from ordinary test and multi-jvm tests
       (Test / executeTests) := Def.uncached {
         val testResults = (Test / executeTests).value
         val multiNodeResults = multiExecuteTests.value
         // sbt 2: Tests.Output is private[sbt], use bridge from package sbt
         sbt.TestsOutputBridge.mergeOutputs(testResults, multiNodeResults)
       }
     } else Nil) ++
    Def.settings((MultiJvm / compile) := Def.uncached {
      (MultiJvm / headerCreate).value
      (MultiJvm / compile).value
    }) ++ headerSettings(MultiJvm) ++ Seq(validateCompile := Def.uncached { compile.?.all(anyConfigsInThisProject).value })

  implicit class TestResultOps(val self: TestResult) extends AnyVal {
    def id: Int = self match {
      case TestResult.Passed => 0
      case TestResult.Failed => 1
      case TestResult.Error  => 2
    }
  }
}

/**
 * Additional settings for scalatest.
 */
object MultiNodeScalaTest extends AutoPlugin {

  override lazy val requires = MultiNode

  override lazy val projectSettings =
    Seq(
      MultiJvm / extraOptions := {
        val src = (MultiJvm / sourceDirectory).value
        (name: String) => (src ** (name + ".conf")).get().headOption.map("-Dpekko.config=" + _.absolutePath).toSeq
      },
      MultiJvm / scalatestOptions := {
        Seq("-C", "org.scalatest.extra.QuietReporter") ++
        (if (excludeTestTags.value.isEmpty) Seq.empty
         else
           Seq(
             "-l",
             if (MultiNode.CliOptions.multiNode.get) excludeTestTags.value.mkString("\"", " ", "\"")
             else excludeTestTags.value.mkString(" "))) ++
        (if (onlyTestTags.value.isEmpty) Seq.empty
         else
           Seq(
             "-n",
             if (MultiNode.CliOptions.multiNode.get) onlyTestTags.value.mkString("\"", " ", "\"")
             else onlyTestTags.value.mkString(" ")))
      })
}
