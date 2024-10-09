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

import JdkOptions.autoImport._
import MultiJvmPlugin.autoImport.MultiJvm

import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys._
import sbt.Def
import sbt.Keys._
import sbt._
import sbtassembly.AssemblyPlugin.autoImport._
import sbtwelcome.WelcomePlugin.autoImport._
import _root_.io.github.siculo.sbtbom.BomSbtPlugin.autoImport.makeBom

import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

object PekkoBuild {

  object CliOptions {
    // CI is the env var defined by Github Actions and Travis:
    // - https://docs.github.com/en/actions/reference/environment-variables#default-environment-variables
    // - https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
    val runningOnCi: CliOption[Boolean] = CliOption("pekko.ci-server", sys.env.contains("CI"))
  }

  val enableMiMa = true

  val parallelExecutionByDefault = false // TODO: enable this once we're sure it does not break things

  lazy val rootSettings = Def.settings(
    UnidocRoot.pekkoSettings,
    Protobuf.settings,
    GlobalScope / parallelExecution := System
      .getProperty("pekko.parallelExecution", parallelExecutionByDefault.toString)
      .toBoolean,
    // used for linking to API docs (overwrites `project-info.version`)
    ThisBuild / projectInfoVersion := { if (isSnapshot.value) "snapshot" else version.value })

  lazy val mayChangeSettings = Seq(description := """|This module of Apache Pekko is marked as
                      |'may change', which means that it is in early
                      |access mode. A module marked 'may change' doesn't
                      |have to obey the rule of staying binary compatible
                      |between minor releases. Breaking API changes may be
                      |introduced in minor releases without notice as we
                      |refine and simplify based on your feedback. Additionally
                      |such a module may be dropped in major releases
                      |without prior deprecation.
                      |""".stripMargin)

  val (mavenLocalResolver, mavenLocalResolverSettings) =
    System.getProperty("pekko.build.M2Dir") match {
      case null => (Resolver.mavenLocal, Seq.empty)
      case path =>
        // Maven resolver settings
        def deliverPattern(outputPath: File): String =
          (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

        val resolver = Resolver.file("user-publish-m2-local", new File(path))
        (
          resolver,
          Seq(
            otherResolvers := resolver :: publishTo.value.toList,
            publishM2Configuration := Classpaths.publishConfig(
              publishMavenStyle.value,
              deliverPattern(crossTarget.value),
              if (isSnapshot.value) "integration" else "release",
              ivyConfigurations.value.map(c => ConfigRef(c.name)).toVector,
              artifacts = packagedArtifacts.value.toVector,
              resolverName = resolver.name,
              checksums = (publishM2 / checksums).value.toVector,
              logging = ivyLoggingLevel.value,
              overwrite = true)))
    }

  lazy val resolverSettings = Def.settings(
    pomIncludeRepository := (_ => false) // do not leak internal repositories during staging
  )

  private def allWarnings: Boolean = System.getProperty("pekko.allwarnings", "false").toBoolean

  final val DefaultScalacOptions = Def.setting {
    if (scalaVersion.value.startsWith("3.")) {
      Seq(
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked",
        // 'blessed' since 2.13.1
        "-language:higherKinds")
    } else {
      Seq(
        "-encoding",
        "UTF-8",
        "-feature",
        "-unchecked",
        "-Xlog-reflective-calls",
        // 'blessed' since 2.13.1
        "-language:higherKinds")
    }
  }

  private def jvmGCLogOptions(isJdk11OrHigher: Boolean, isJdk8: Boolean): Seq[String] = {
    if (isJdk11OrHigher)
      // -Xlog:gc* is equivalent to -XX:+PrintGCDetails. See:
      // https://docs.oracle.com/en/java/javase/11/tools/java.html#GUID-BE93ABDC-999C-4CB5-A88B-1994AAAC74D5
      Seq("-Xlog:gc*")
    else if (isJdk8) Seq("-XX:+PrintGCTimeStamps", "-XX:+PrintGCDetails")
    else Nil
  }

  // -XDignore.symbol.file suppresses sun.misc.Unsafe warnings
  final val DefaultJavacOptions = Seq("-encoding", "UTF-8", "-Xlint:unchecked", "-XDignore.symbol.file")

  lazy val defaultSettings: Seq[Setting[_]] = Def.settings(
    projectInfoVersion := (if (isSnapshot.value) "snapshot" else version.value),
    Dependencies.Versions,
    resolverSettings,
    TestExtras.Filter.settings,
    // compile options
    Compile / scalacOptions ++= DefaultScalacOptions.value,
    Compile / scalacOptions ++=
      JdkOptions.targetJdkScalacOptions(
        targetSystemJdk.value,
        optionalDir(jdk8home.value),
        fullJavaHomes.value,
        scalaVersion.value),
    Compile / scalacOptions ++= (if (allWarnings) Seq("-deprecation") else Nil),
    Test / scalacOptions := (Test / scalacOptions).value.filterNot(opt =>
      opt == "-Xlog-reflective-calls" || opt.contains("genjavadoc")),
    Compile / javacOptions ++= {
      DefaultJavacOptions ++
      JdkOptions.targetJdkJavacOptions(targetSystemJdk.value, optionalDir(jdk8home.value), fullJavaHomes.value)
    },
    Test / javacOptions ++= DefaultJavacOptions ++
    JdkOptions.targetJdkJavacOptions(targetSystemJdk.value, optionalDir(jdk8home.value), fullJavaHomes.value),
    Compile / javacOptions ++= (if (allWarnings) Seq("-Xlint:deprecation") else Nil),
    doc / javacOptions := Seq(),
    crossVersion := CrossVersion.binary,
    // Adds a `src/main/scala-2.13+` source directory for code shared
    // between Scala 2.13 and Scala 3
    Compile / unmanagedSourceDirectories ++= {
      val sourceDir = (Compile / sourceDirectory).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, n))            => Seq(sourceDir / "scala-2.13+")
        case Some((2, n)) if n >= 13 => Seq(sourceDir / "scala-2.13+")
        case _                       => Nil
      }
    },
    // Adds a `src/test/scala-2.13+` source directory for code shared
    // between Scala 2.13 and Scala 3
    Test / unmanagedSourceDirectories ++= {
      val sourceDir = (Test / sourceDirectory).value
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, n))            => Seq(sourceDir / "scala-2.13+")
        case Some((2, n)) if n >= 13 => Seq(sourceDir / "scala-2.13+")
        case _                       => Nil
      }
    },
    ThisBuild / ivyLoggingLevel := UpdateLogging.Quiet,
    licenses := Seq(("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))),
    homepage := Some(url("https://pekko.apache.org/")),
    description := "Apache Pekko is a toolkit for building highly concurrent, distributed, and resilient message-driven applications for Java and Scala.",
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/apache/pekko"),
        "scm:git:https://github.com/apache/pekko.git",
        "scm:git:git@github.com:apache/pekko.git")),
    apiURL := Some(url(s"https://pekko.apache.org/api/pekko/${version.value}")),
    initialCommands :=
      """|import language.postfixOps
         |import org.apache.pekko.actor._
         |import scala.concurrent._
         |import com.typesafe.config.ConfigFactory
         |import scala.concurrent.duration._
         |import org.apache.pekko.util.Timeout
         |var config = ConfigFactory.parseString("pekko.stdout-loglevel=INFO,pekko.loglevel=DEBUG,pinned{type=PinnedDispatcher,executor=thread-pool-executor,throughput=1000}")
         |var remoteConfig = ConfigFactory.parseString("pekko.remote.classic.netty{port=0,use-dispatcher-for-io=pekko.actor.default-dispatcher,execution-pool-size=0},pekko.actor.provider=remote").withFallback(config)
         |var system: ActorSystem = null
         |implicit def _system: ActorSystem = system
         |def startSystem(remoting: Boolean = false) = { system = ActorSystem("repl", if(remoting) remoteConfig else config); println("don’t forget to system.terminate()!") }
         |implicit def ec: ExecutionContext = system.dispatcher
         |implicit val timeout: Timeout = Timeout(5 seconds)
         |""".stripMargin,
    /**
     * Test settings
     */
    Test / fork := true,
    // default JVM config for tests
    Test / javaOptions ++= {
      val defaults = Seq(
        // ## core memory settings
        "-XX:+UseG1GC",
        // most tests actually don't really use _that_ much memory (>1g usually)
        // twice used (and then some) keeps G1GC happy - very few or to no full gcs
        "-Xms3g",
        "-Xmx3g",
        // increase stack size (todo why?)
        "-Xss2m",
        // ## extra memory/gc tuning
        // this breaks jstat, but could avoid costly syncs to disc see https://www.evanjones.ca/jvm-mmap-pause.html
        "-XX:+PerfDisableSharedMem",
        // tell G1GC that we would be really happy if all GC pauses could be kept below this as higher would
        // likely start causing test failures in timing tests
        "-XX:MaxGCPauseMillis=300",
        // nio direct memory limit for artery/aeron (probably)
        "-XX:MaxDirectMemorySize=256m",
        // faster random source
        "-Djava.security.egd=file:/dev/./urandom")

      defaults ++ CliOptions.runningOnCi
        .ifTrue(jvmGCLogOptions(JdkOptions.isJdk11orHigher, JdkOptions.isJdk8))
        .getOrElse(Nil) ++
      JdkOptions.versionSpecificJavaOptions
    },
    // all system properties passed to sbt prefixed with "pekko." or "aeron." will be passed on to the forked jvms as is
    Test / javaOptions := {
      val base = (Test / javaOptions).value
      val knownPrefix = Set("pekko.", "aeron.")
      val pekkoSysProps: Seq[String] =
        sys.props.iterator.collect {
          case (key, value) if knownPrefix.exists(pre => key.startsWith(pre)) => s"-D$key=$value"
        }.toList

      base ++ pekkoSysProps
    },
    // with forked tests the working directory is set to each module's home directory
    // rather than the Akka root, some tests depend on Akka root being working dir, so reset
    Test / testGrouping := {
      val original: Seq[Tests.Group] = (Test / testGrouping).value

      original.map { group =>
        group.runPolicy match {
          case Tests.SubProcess(forkOptions) =>
            // format: off
            group.withRunPolicy(Tests.SubProcess(
              forkOptions.withWorkingDirectory(workingDirectory = Some(new File(System.getProperty("user.dir"))))))
            // format: on
          case _ => group
        }
      }
    },
    Test / parallelExecution := System
      .getProperty("pekko.parallelExecution", parallelExecutionByDefault.toString)
      .toBoolean,
    Test / logBuffered := System.getProperty("pekko.logBufferedTests", "false").toBoolean,
    // show full stack traces and test case durations
    Test / testOptions += Tests.Argument("-oDF"),
    mavenLocalResolverSettings,
    docLintingSettings,
    JdkOptions.targetJdkSettings,
    // needed until https://github.com/siculo/sbt-bom/pull/57 has been merged
    packagedArtifacts += {
      Artifact(artifact.value.name, "cyclonedx", "xml") -> makeBom.value
    },
    // a workaround for https://github.com/akka/akka/issues/27661
    // see also project/Protobuf.scala that introduces /../ to make "intellij happy"
    MultiJvm / assembly / fullClasspath := {
      val old = (MultiJvm / assembly / fullClasspath).value.toVector
      val files = old.map(_.data.getCanonicalFile).distinct
      files.map { x =>
        Attributed.blank(x)
      }
    })

  lazy val welcomeSettings: Seq[Setting[_]] = Def.settings {
    import sbtwelcome._
    Seq(
      logo := {
        raw"""
           |________     ______ ______        
           |___  __ \_______  /____  /_______ 
           |__  /_/ /  _ \_  //_/_  //_/  __ \
           |_  ____//  __/  ,<  _  ,<  / /_/ /
           |/_/     \___//_/|_| /_/|_| \____/   ${version.value}
           |
           |""".stripMargin

      },
      logoColor := scala.Console.BLUE,
      usefulTasks := Seq(
        UsefulTask("compile", "Compile the current project"),
        UsefulTask("test", "Run all the tests"),
        UsefulTask("testQuick",
          "Runs all the tests. When run multiple times will only run previously failing tests (shell mode only)"),
        UsefulTask("testOnly *.AnySpec", "Only run a selected test"),
        UsefulTask("testQuick *.AnySpec",
          "Only run a selected test. When run multiple times will only run previously failing tests (shell mode only)"),
        UsefulTask("testQuickUntilPassed", "Runs all tests in a continuous loop until all tests pass"),
        UsefulTask("publishLocal", "Publish current snapshot version to local ~/.ivy2 repo"),
        UsefulTask("verifyCodeStyle", "Verify code style"),
        UsefulTask("applyCodeStyle", "Apply code style"),
        UsefulTask("sortImports", "Sort the imports"),
        UsefulTask("mimaReportBinaryIssues ", "Check binary issues"),
        UsefulTask("validatePullRequest ", "Validate pull request"),
        UsefulTask("docs/paradox", "Build documentation (license report will be generate on CI or Publish)"),
        UsefulTask("docs/paradoxBrowse",
          "Browse the generated documentation (license report will be generate on CI or Publish)"),
        UsefulTask("tips:", "prefix commands with `+` to run against cross Scala versions."),
        UsefulTask("Contributing guide:", "https://github.com/apache/pekko/blob/main/CONTRIBUTING.md")).map(
        _.noAlias))
  }

  private def optionalDir(path: String): Option[File] =
    Option(path).filter(_.nonEmpty).map { path =>
      val dir = new File(path)
      if (!dir.exists)
        throw new IllegalArgumentException(s"Path [$path] not found")
      dir
    }

  lazy val docLintingSettings = Seq(
    compile / javacOptions ++= Seq("-Xdoclint:none"),
    test / javacOptions ++= Seq("-Xdoclint:none"),
    doc / javacOptions ++= {
      if (JdkOptions.isJdk8) Seq("-Xdoclint:none")
      else Seq("-Xdoclint:none", "--ignore-source-errors")
    })

  def loadSystemProperties(fileName: String): Unit = {
    import scala.collection.JavaConverters._
    val file = new File(fileName)
    if (file.exists()) {
      println("Loading system properties from file `" + fileName + "`")
      val in = new InputStreamReader(new FileInputStream(file), "UTF-8")
      val props = new Properties
      props.load(in)
      in.close()
      sys.props ++ props.asScala
    }
  }

  def majorMinor(version: String): Option[String] = """\d+\.\d+""".r.findFirstIn(version)
}
