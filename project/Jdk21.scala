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

object Jdk21 extends AutoPlugin {
  import JdkOptions.JavaVersion._

  private val supportedJavaLTSVersions = List("21")

  lazy val CompileJdk21 = config("CompileJdk21").extend(Compile)

  lazy val TestJdk21 = config("TestJdk21").extend(Test).extend(CompileJdk21)

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

  lazy val compileJdk21Settings = Seq(
    unmanagedSourceDirectories := additionalSourceDirectories.value,
    scalacOptions := PekkoBuild.DefaultScalacOptions.value ++ Seq("-release", majorVersion.toString),
    javacOptions := PekkoBuild.DefaultJavacOptions ++ Seq("--release", majorVersion.toString))

  lazy val testJdk21Settings = Seq(
    unmanagedSourceDirectories := additionalTestSourceDirectories.value,
    scalacOptions := PekkoBuild.DefaultScalacOptions.value ++ Seq("-release", majorVersion.toString),
    javacOptions := PekkoBuild.DefaultJavacOptions ++ Seq("--release", majorVersion.toString),
    compile := compile.dependsOn(CompileJdk21 / compile).value,
    classpathConfiguration := TestJdk21,
    externalDependencyClasspath := (Test / externalDependencyClasspath).value)

  lazy val compileSettings = Seq(
    Compile / dependencyClasspathAsJars ++= (CompileJdk21 / exportedProducts).value)

  lazy val testSettings = Seq((Test / test) := {
    (Test / test).value
    (TestJdk21 / test).value
  })

  override lazy val trigger = noTrigger
  override lazy val projectConfigurations = Seq(CompileJdk21)
  override lazy val projectSettings =
    inConfig(CompileJdk21)(Defaults.compileSettings) ++
    inConfig(CompileJdk21)(compileJdk21Settings) ++
    compileSettings ++
    inConfig(TestJdk21)(Defaults.testSettings) ++
    inConfig(TestJdk21)(testJdk21Settings) ++
    testSettings
}
