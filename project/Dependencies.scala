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

import sbt._
import Keys._
import scala.language.implicitConversions

object Dependencies {
  import DependencyHelpers._

  object Protobuf {
    // https://protobuf.dev/support/version-support/
    // protobuf-java 4.x corresponds with protoc 26.x-33.x
    val protobufJavaVersion = "4.33.2"
    val protocVersion = "33.0"
  }

  val junitVersion = "4.13.2"
  val junit6Version = "6.0.1"
  val slf4jVersion = "2.0.17"
  // also update agrona version when updating aeron:
  val aeronVersion = "1.48.10"
  // Use the major+minor agrona versions matching aeron at
  // https://github.com/aeron-io/aeron/blob/1.x.y/gradle/libs.versions.toml
  // (remember to also update the scala-steward pin)
  val agronaVersion = "2.2.4"
  val nettyVersion = "4.2.9.Final"
  val logbackVersion = "1.5.24"

  val jacksonAnnotationsVersion = "2.20"
  val jacksonVersion2 = "2.20.1"
  val jacksonVersion3 = "3.0.3"

  val scala213Version = "2.13.18"
  val scala3Version = "3.3.7"
  val allScalaVersions = Seq(scala213Version, scala3Version)

  val reactiveStreamsVersion = "1.0.4"

  val scalaTestVersion = "3.2.19"
  val scalaTestScalaCheckVersion = "1-18"
  val scalaCheckVersion = "1.18.0"

  val Versions = Seq(crossScalaVersions := allScalaVersions, scalaVersion := allScalaVersions.head)

  object Compile {
    // Compile

    val config = "com.typesafe" % "config" % "1.4.5"
    val `netty-transport` = "io.netty" % "netty-transport" % nettyVersion
    val `netty-handler` = "io.netty" % "netty-handler" % nettyVersion

    val scalaReflect: ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID.versioned("org.scala-lang" % "scala-reflect" % _)

    val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion

    // mirrored in OSGi sample https://github.com/akka/akka-samples/tree/2.6/akka-sample-osgi-dining-hakkers
    val osgiCore = "org.osgi" % "org.osgi.core" % "6.0.0"
    val osgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

    val sigar = "org.fusesource" % "sigar" % "1.6.4"

    val jctools = "org.jctools" % "jctools-core" % "4.0.5"

    // reactive streams
    val reactiveStreams = "org.reactivestreams" % "reactive-streams" % reactiveStreamsVersion

    val lmdb = "org.lmdbjava" % "lmdbjava" % "0.9.1"

    val junit = "junit" % "junit" % junitVersion
    val junit6 = "org.junit.jupiter" % "junit-jupiter-engine" % junit6Version

    val aeronDriver = "io.aeron" % "aeron-driver" % aeronVersion
    val aeronClient = "io.aeron" % "aeron-client" % aeronVersion
    // Added explicitly for when artery tcp is used
    val agrona = "org.agrona" % "agrona" % agronaVersion

    val asnOne = ("com.hierynomus" % "asn-one" % "0.6.0").exclude("org.slf4j", "slf4j-api")

    val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion2
    val jacksonAnnotations = "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonAnnotationsVersion
    val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion2
    val jacksonJdk8 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonVersion2
    val jacksonJsr310 = "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonVersion2
    val jacksonScala = ("com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion2)
      .excludeAll(ExclusionRule(organization = "org.scala-lang"))
    val jacksonParameterNames = "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonVersion2
    val jacksonCbor = "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion2

    val jacksonCore3 = "tools.jackson.core" % "jackson-core" % jacksonVersion3
    val jacksonDatabind3 = "tools.jackson.core" % "jackson-databind" % jacksonVersion3
    val jacksonScala3 = ("tools.jackson.module" %% "jackson-module-scala" % jacksonVersion3)
      .excludeAll(ExclusionRule(organization = "org.scala-lang"))
    val jacksonCbor3 = "tools.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonVersion3

    val lz4Java = "at.yawk.lz4" % "lz4-java" % "1.10.2"

    val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

    object Docs {
      val sprayJson = "io.spray" %% "spray-json" % "1.3.6" % Test
      val gson = "com.google.code.gson" % "gson" % "2.13.2" % Test
    }

    object TestDependencies {
      val commonsIo = "commons-io" % "commons-io" % "2.21.0" % Test
      val commonsCodec = "commons-codec" % "commons-codec" % "1.19.0" % Test
      val junit = "junit" % "junit" % junitVersion % Test
      val junit6 = "org.junit.jupiter" % "junit-jupiter-engine" % junit6Version % Test
      val httpClient = "org.apache.httpcomponents" % "httpclient" % "4.5.14" % Test

      val logback = Compile.logback % Test

      val scalatest = "org.scalatest" %% "scalatest" % scalaTestVersion % Test

      // The 'scalaTestPlus' projects are independently versioned,
      // but the version of each module starts with the scalatest
      // version it was intended to work with
      val scalatestJUnit = "org.scalatestplus" %% "junit-4-13" % (scalaTestVersion + ".0") % Test
      val scalatestTestNG = "org.scalatestplus" %% "testng-7-5" % "3.2.17.0" % Test
      val scalatestScalaCheck =
        "org.scalatestplus" %% s"scalacheck-$scalaTestScalaCheckVersion" % (scalaTestVersion + ".0") % Test
      // https://github.com/scalatest/scalatest/issues/2311
      val scalatestMockito = "org.scalatestplus" %% "mockito-4-11" % "3.2.18.0" % Test

      val pojosr = "com.googlecode.pojosr" % "de.kalpatec.pojosr.framework" % "0.2.1" % Test
      val tinybundles = "org.ops4j.pax.tinybundles" % "tinybundles" % "4.0.1" % Test
      val bndlib = "biz.aQute.bnd" % "biz.aQute.bndlib" % "7.2.0" % Test
      val `pax-exam` = "org.ops4j.pax.exam" % "pax-exam" % "4.14.0" % Test
      val `pax-exam-cm` = "org.ops4j.pax.exam" % "pax-exam-cm" % "4.14.0" % Test
      val `pax-exam-container-forked` = "org.ops4j.pax.exam" % "pax-exam-container-forked" % "4.14.0" % Test

      // in-memory filesystem for file related tests
      val jimfs = "com.google.jimfs" % "jimfs" % "1.3.1" % Test

      val dockerClientVersion = "3.7.0"
      val dockerClient = Seq(
        "com.github.docker-java" % "docker-java-core" % dockerClientVersion % Test,
        "com.github.docker-java" % "docker-java-transport-httpclient5" % dockerClientVersion % Test)

      val jackson = Seq(
        jacksonCore % Test,
        jacksonAnnotations % Test,
        jacksonDatabind % Test,
        "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-base" % jacksonVersion2 % Test,
        "com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % jacksonVersion2 % Test,
        "com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % jacksonVersion2 % Test)

      // metrics, measurements, perf testing
      val metrics = "io.dropwizard.metrics" % "metrics-core" % "4.2.37" % Test
      val metricsJvm = "io.dropwizard.metrics" % "metrics-jvm" % "4.2.37" % Test
      val latencyUtils = "org.latencyutils" % "LatencyUtils" % "2.0.3" % Test
      val hdrHistogram = "org.hdrhistogram" % "HdrHistogram" % "2.2.2" % Test
      val metricsAll = Seq(metrics, metricsJvm, latencyUtils, hdrHistogram)

      // sigar logging
      val slf4jJul = "org.slf4j" % "jul-to-slf4j" % slf4jVersion % Test
      val slf4jLog4j = "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % Test

      // reactive streams tck
      val reactiveStreamsTck = ("org.reactivestreams" % "reactive-streams-tck" % reactiveStreamsVersion % Test)
        .exclude("org.testng", "testng")

      val protobufRuntime = "com.google.protobuf" % "protobuf-java" % Protobuf.protobufJavaVersion % Test

      // YCSB (Yahoo Cloud Serving Benchmark https://ycsb.site)
      val ycsb = "site.ycsb" % "core" % "0.17.0" % Test
    }

    object Provided {
      // TODO remove from "test" config
      val sigarLoader = "io.kamon" % "sigar-loader" % "1.6.6" % "optional;provided;test"

      val activation = "com.sun.activation" % "javax.activation" % "1.2.0" % "provided;test"

      val levelDB = "org.iq80.leveldb" % "leveldb" % "0.12" % "optional;provided"
      val levelDBmultiJVM = "org.iq80.leveldb" % "leveldb" % "0.12" % "optional;provided;multi-jvm;test"
      val levelDBNative = "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8" % "optional;provided"

      val junit = Compile.junit % "optional;provided;test"
      val junit6 = Compile.junit6 % "optional;provided;test"

      val scalatest = "org.scalatest" %% "scalatest" % scalaTestVersion % "optional;provided;test"

      val logback = Compile.logback % "optional;provided;test"

      val protobufRuntime = "com.google.protobuf" % "protobuf-java" % Protobuf.protobufJavaVersion % "optional;provided"

    }

  }

  import Compile._
  // TODO check if `l ++=` everywhere expensive?
  lazy val l = libraryDependencies

  lazy val actor = l ++= Seq(config)

  val actorTyped = l ++= Seq(slf4jApi)

  val discovery = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest)

  val coordination = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest)

  val testkit = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest) ++ TestDependencies.metricsAll

  // TestDependencies.dockerClient brings in older versions of jackson libs that have CVEs
  val actorTests = l ++= Seq(
    TestDependencies.junit,
    TestDependencies.scalatest,
    TestDependencies.scalatestJUnit,
    TestDependencies.scalatestScalaCheck,
    TestDependencies.commonsCodec,
    TestDependencies.jimfs) ++
  TestDependencies.jackson ++ TestDependencies.dockerClient

  lazy val actorTestkitTyped = l ++= Seq(
    Provided.logback,
    Provided.junit,
    Provided.junit6,
    Provided.scalatest,
    TestDependencies.scalatestJUnit)

  lazy val pki = l ++=
    Seq(
      asnOne,
      // pull up slf4j version from the one provided transitively in asnOne to fix unidoc
      Compile.slf4jApi,
      TestDependencies.scalatest)

  val remoteDependencies = Seq(`netty-transport`, `netty-handler`, aeronDriver, aeronClient)
  val remoteOptionalDependencies = remoteDependencies.map(_ % "optional")

  lazy val remote = l ++= Seq(
    agrona,
    TestDependencies.junit,
    TestDependencies.scalatest,
    TestDependencies.jimfs,
    TestDependencies.protobufRuntime) ++ remoteOptionalDependencies

  lazy val remoteTests = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest) ++ remoteDependencies

  lazy val multiNodeTestkit = l ++= Seq(`netty-transport`, `netty-handler`)

  lazy val cluster = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest, TestDependencies.logback)

  lazy val clusterTools = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest)

  lazy val clusterSharding = l ++= Seq(
    Provided.levelDBmultiJVM,
    Provided.levelDBNative,
    TestDependencies.junit,
    TestDependencies.scalatest,
    TestDependencies.commonsIo,
    TestDependencies.ycsb)

  lazy val clusterMetrics = l ++= Seq(
    Provided.sigarLoader,
    TestDependencies.slf4jJul,
    TestDependencies.slf4jLog4j,
    TestDependencies.logback,
    TestDependencies.scalatestMockito)

  lazy val distributedData = l ++= Seq(lmdb, TestDependencies.junit, TestDependencies.scalatest)

  lazy val slf4j = l ++= Seq(slf4jApi, TestDependencies.logback)

  lazy val persistence = l ++= Seq(
    Provided.levelDB,
    Provided.levelDBNative,
    TestDependencies.scalatest,
    TestDependencies.scalatestJUnit,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    TestDependencies.commonsCodec)

  lazy val persistenceQuery = l ++= Seq(
    TestDependencies.scalatest,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    Provided.levelDB,
    Provided.levelDBNative)

  lazy val persistenceTck = l ++= Seq(
    TestDependencies.scalatest.withConfigurations(Some("compile")),
    TestDependencies.junit.withConfigurations(Some("compile")),
    Provided.levelDB,
    Provided.levelDBNative)

  lazy val persistenceTestKit = l ++= Seq(TestDependencies.scalatest, TestDependencies.logback)

  lazy val persistenceTypedTests = l ++= Seq(TestDependencies.scalatest, TestDependencies.logback)

  lazy val persistenceShared = l ++= Seq(Provided.levelDB, Provided.levelDBNative, TestDependencies.logback)

  lazy val jackson = l ++= Seq(
    jacksonCore,
    jacksonAnnotations,
    jacksonDatabind,
    jacksonJdk8,
    jacksonJsr310,
    jacksonParameterNames,
    jacksonCbor,
    jacksonScala,
    lz4Java,
    TestDependencies.junit,
    TestDependencies.scalatest)

  lazy val jackson3 = l ++= Seq(
    jacksonCore3,
    jacksonAnnotations,
    jacksonDatabind3,
    jacksonCbor3,
    jacksonScala3,
    lz4Java,
    TestDependencies.junit,
    TestDependencies.scalatest)

  lazy val osgi = l ++= Seq(
    osgiCore,
    osgiCompendium,
    TestDependencies.logback,
    TestDependencies.commonsIo,
    TestDependencies.pojosr,
    TestDependencies.tinybundles,
    TestDependencies.bndlib,
    TestDependencies.`pax-exam`,
    TestDependencies.`pax-exam-cm`,
    TestDependencies.`pax-exam-container-forked`,
    TestDependencies.scalatest,
    TestDependencies.junit)

  lazy val docs = l ++= Seq(
    TestDependencies.scalatest,
    TestDependencies.junit,
    Docs.sprayJson,
    Docs.gson,
    Provided.levelDB)

  lazy val benchJmh = l ++= Seq(logback, Provided.levelDB, Provided.levelDBNative, Compile.jctools)

  // pekko stream

  lazy val stream = l ++= Seq[sbt.ModuleID](reactiveStreams, TestDependencies.scalatest)

  lazy val streamTestkit = l ++= Seq(
    TestDependencies.scalatest,
    TestDependencies.scalatestScalaCheck,
    TestDependencies.junit)

  lazy val streamTests = l ++= Seq(
    TestDependencies.scalatest,
    TestDependencies.scalatestScalaCheck,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    TestDependencies.jimfs)

  lazy val streamTestsTck = l ++= Seq(
    TestDependencies.scalatest,
    TestDependencies.scalatestTestNG,
    TestDependencies.scalatestScalaCheck,
    TestDependencies.junit,
    TestDependencies.reactiveStreamsTck)

}

object DependencyHelpers {
  case class ScalaVersionDependentModuleID(modules: String => Seq[ModuleID]) {
    def %(config: String): ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID(version => modules(version).map(_ % config))
  }
  object ScalaVersionDependentModuleID {
    implicit def liftConstantModule(mod: ModuleID): ScalaVersionDependentModuleID = versioned(_ => mod)

    def versioned(f: String => ModuleID): ScalaVersionDependentModuleID = ScalaVersionDependentModuleID(v => Seq(f(v)))
    def fromPF(f: PartialFunction[String, ModuleID]): ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID(version => if (f.isDefinedAt(version)) Seq(f(version)) else Nil)
  }

  /**
   * Use this as a dependency setting if the dependencies contain both static and Scala-version
   * dependent entries.
   */
  def versionDependentDeps(modules: ScalaVersionDependentModuleID*): Def.Setting[Seq[ModuleID]] =
    libraryDependencies ++= modules.flatMap(m => m.modules(scalaVersion.value))

  val ScalaVersion = """\d\.\d+\.\d+(?:-(?:M|RC)\d+)?""".r
  val nominalScalaVersion: String => String = {
    // matches:
    // 2.12.0-M1
    // 2.12.0-RC1
    // 2.12.0
    case version @ ScalaVersion() => version
    // transforms 2.12.0-custom-version to 2.12.0
    case version => version.takeWhile(_ != '-')
  }
}
