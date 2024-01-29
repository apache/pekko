/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._

object Jdk9 extends AutoPlugin {
  import JdkOptions.notOnJdk8
  import JdkOptions.JavaVersion._

  // The version 9 is special for any Java versions >= 9
  // and the version 11 is special for any Java versions >= 11
  // and the version 17 is special for any Java versions >= 17
  // and the version 21 is special for any Java versions >= 21
  private val supportedJavaLTSVersions = List("9", "11", "17", "21")

  lazy val CompileJdk9 = config("CompileJdk9").extend(Compile)

  lazy val TestJdk9 = config("TestJdk9").extend(Test).extend(CompileJdk9)

  lazy val ScalaSourceDirectories: Seq[String] = getAdditionalSourceDirectoryNames("scala")
  lazy val ScalaTestSourceDirectories: Seq[String] = getAdditionalSourceDirectoryNames("scala", isTest = true)

  lazy val JavaSourceDirectories: Seq[String] = getAdditionalSourceDirectoryNames("java")
  lazy val JavaTestSourceDirectories: Seq[String] = getAdditionalSourceDirectoryNames("java", isTest = true)

  lazy val additionalSourceDirectories =
    getAdditionalSourceDirectories(Compile, ScalaSourceDirectories ++ JavaSourceDirectories)

  lazy val additionalTestSourceDirectories =
    getAdditionalSourceDirectories(Test, ScalaTestSourceDirectories ++ JavaTestSourceDirectories)

  private def getAdditionalSourceDirectoryNames(language: String, isTest: Boolean = false): Seq[String] = {
    for {
      version <- supportedJavaLTSVersions if version.toInt <= majorVersion
    } yield {
      if (isTest) {
        s"$language-jdk$version-only"
      } else {
        s"$language-jdk-$version"
      }
    }
  }

  private def getAdditionalSourceDirectories(task: Configuration, sourceDirectoryNames: Seq[String]) = Def.setting {
    for (sourceDirectoryName <- sourceDirectoryNames)
      yield (task / sourceDirectory).value / sourceDirectoryName
  }

  lazy val compileJdk9Settings = Seq(
    // following the scala-2.12, scala-sbt-1.0, ... convention
    unmanagedSourceDirectories := notOnJdk8(additionalSourceDirectories.value),
    scalacOptions := PekkoBuild.DefaultScalacOptions.value ++ notOnJdk8(Seq("-release", majorVersion.toString)),
    javacOptions := PekkoBuild.DefaultJavacOptions ++ notOnJdk8(Seq("--release", majorVersion.toString)))

  lazy val testJdk9Settings = Seq(
    // following the scala-2.12, scala-sbt-1.0, ... convention
    unmanagedSourceDirectories := notOnJdk8(additionalTestSourceDirectories.value),
    scalacOptions := PekkoBuild.DefaultScalacOptions.value ++ notOnJdk8(Seq("-release", majorVersion.toString)),
    javacOptions := PekkoBuild.DefaultJavacOptions ++ notOnJdk8(Seq("--release", majorVersion.toString)),
    compile := compile.dependsOn(CompileJdk9 / compile).value,
    classpathConfiguration := TestJdk9,
    externalDependencyClasspath := (Test / externalDependencyClasspath).value)

  lazy val compileSettings = Seq(
    // It might have been more 'neat' to add the jdk9 products to the jar via packageBin/mappings, but that doesn't work with the OSGi plugin,
    // so we add them to the fullClasspath instead.
    //    Compile / packageBin / mappings
    //      ++= (CompileJdk9 / products).value.flatMap(Path.allSubpaths),
    // Since sbt-osgi upgrade to 0.9.5, the fullClasspath is no longer used on packaging when use sbt-osgi, so we have to
    // add jdk9 products to dependencyClasspathAsJars instead.
    //    Compile / fullClasspath ++= (CompileJdk9 / exportedProducts).value)
    Compile / dependencyClasspathAsJars ++= notOnJdk8((CompileJdk9 / exportedProducts).value))

  lazy val testSettings = Seq((Test / test) := {
    (Test / test).value
    (TestJdk9 / test).value
  })

  override lazy val trigger = noTrigger
  override lazy val projectConfigurations = Seq(CompileJdk9)
  override lazy val projectSettings =
    inConfig(CompileJdk9)(Defaults.compileSettings) ++
    inConfig(CompileJdk9)(compileJdk9Settings) ++
    compileSettings ++
    inConfig(TestJdk9)(Defaults.testSettings) ++
    inConfig(TestJdk9)(testJdk9Settings) ++
    testSettings
}
