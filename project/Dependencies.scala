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

  lazy val java8CompatVersion = settingKey[String]("The version of scala-java8-compat to use.")
    .withRank(KeyRanks.Invisible) // avoid 'unused key' warning

  val junitVersion = "4.13.2"
  val junit5Version = "5.10.1"
  val slf4jVersion = "2.0.9"
  // check agrona version when updating this
  val aeronVersion = "1.42.1"
  // needs to be inline with the aeron version, check
  // https://github.com/real-logic/aeron/blob/1.x.y/build.gradle
  val agronaVersion = "1.19.2"
  val nettyVersion = "4.1.101.Final"
  val protobufJavaVersion = "3.20.3"
  val logbackVersion = "1.3.11"

  val jacksonCoreVersion = "2.14.3"
  val jacksonDatabindVersion = jacksonCoreVersion

  val scala212Version = "2.12.18"
  val scala213Version = "2.13.12"
  val scala3Version = "3.3.1"
  val allScalaVersions = Seq(scala213Version, scala212Version, scala3Version)

  val reactiveStreamsVersion = "1.0.4"

  val sslConfigVersion = "0.6.1"

  val scalaTestVersion = "3.2.17"
  val scalaTestScalaCheckVersion = "1-17"
  val scalaCheckVersion = "1.17.0"

  val Versions =
    Seq(crossScalaVersions := allScalaVersions, scalaVersion := allScalaVersions.head,
      java8CompatVersion := "1.0.2")

  object Compile {
    // Compile

    val config = "com.typesafe" % "config" % "1.4.3"
    val `netty-transport` = "io.netty" % "netty-transport" % nettyVersion
    val `netty-handler` = "io.netty" % "netty-handler" % nettyVersion

    val scalaReflect: ScalaVersionDependentModuleID =
      ScalaVersionDependentModuleID.versioned("org.scala-lang" % "scala-reflect" % _)

    val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion

    // mirrored in OSGi sample https://github.com/akka/akka-samples/tree/2.6/akka-sample-osgi-dining-hakkers
    val osgiCore = "org.osgi" % "org.osgi.core" % "6.0.0"
    val osgiCompendium = "org.osgi" % "org.osgi.compendium" % "5.0.0"

    val sigar = "org.fusesource" % "sigar" % "1.6.4"

    val jctools = "org.jctools" % "jctools-core" % "4.0.2"

    // reactive streams
    val reactiveStreams = "org.reactivestreams" % "reactive-streams" % reactiveStreamsVersion

    // ssl-config
    val sslConfigCore = Def.setting {
      "com.typesafe" %% "ssl-config-core" % sslConfigVersion
    }

    val lmdb = "org.lmdbjava" % "lmdbjava" % "0.8.3"

    val junit = "junit" % "junit" % junitVersion
    val junit5 = "org.junit.jupiter" % "junit-jupiter-engine" % junit5Version

    // For Java 8 Conversions
    val java8Compat = Def.setting {
      "org.scala-lang.modules" %% "scala-java8-compat" % java8CompatVersion.value
    }

    val aeronDriver = "io.aeron" % "aeron-driver" % aeronVersion
    val aeronClient = "io.aeron" % "aeron-client" % aeronVersion
    // Added explicitly for when artery tcp is used
    val agrona = "org.agrona" % "agrona" % agronaVersion

    val asnOne = ("com.hierynomus" % "asn-one" % "0.6.0").exclude("org.slf4j", "slf4j-api")

    val jacksonCore = Def.setting {
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonCoreVersion
    } // ApacheV2
    val jacksonAnnotations = Def.setting {
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonCoreVersion
    } // ApacheV2
    val jacksonDatabind = Def.setting {
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion
    } // ApacheV2
    val jacksonJdk8 = Def.setting {
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jdk8" % jacksonCoreVersion
    } // ApacheV2
    val jacksonJsr310 = Def.setting {
      "com.fasterxml.jackson.datatype" % "jackson-datatype-jsr310" % jacksonCoreVersion
    } // ApacheV2
    val jacksonScala = Def.setting {
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonCoreVersion
    } // ApacheV2
    val jacksonParameterNames = Def.setting {
      "com.fasterxml.jackson.module" % "jackson-module-parameter-names" % jacksonCoreVersion
    } // ApacheV2
    val jacksonCbor = Def.setting {
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-cbor" % jacksonCoreVersion
    } // ApacheV2
    val lz4Java = "org.lz4" % "lz4-java" % "1.8.0"

    val logback = "ch.qos.logback" % "logback-classic" % logbackVersion

    object Docs {
      val sprayJson = "io.spray" %% "spray-json" % "1.3.6" % Test
      val gson = "com.google.code.gson" % "gson" % "2.10.1" % Test
    }

    object TestDependencies {
      val bcpkix = "org.bouncycastle" % "bcpkix-jdk18on" % "1.77" % Test
      val commonsMath = "org.apache.commons" % "commons-math" % "2.2" % Test
      val commonsIo = "commons-io" % "commons-io" % "2.15.0" % Test
      val commonsCodec = "commons-codec" % "commons-codec" % "1.16.0" % Test
      val commonsCompress = "org.apache.commons" % "commons-compress" % "1.25.0" % Test
      val junit = "junit" % "junit" % junitVersion % Test
      val junit5 = "org.junit.jupiter" % "junit-jupiter-engine" % junit5Version % Test
      val httpClient = "org.apache.httpcomponents" % "httpclient" % "4.5.14" % Test

      val logback = Compile.logback % Test

      val scalatest = Def.setting { "org.scalatest" %% "scalatest" % scalaTestVersion % Test } // ApacheV2

      // The 'scalaTestPlus' projects are independently versioned,
      // but the version of each module starts with the scalatest
      // version it was intended to work with
      val scalatestJUnit = Def.setting {
        "org.scalatestplus" %% "junit-4-13" % (scalaTestVersion + ".0") % Test
      }
      val scalatestTestNG = Def.setting {
        "org.scalatestplus" %% "testng-7-5" % (scalaTestVersion + ".0") % Test
      }
      val scalatestScalaCheck = Def.setting {
        "org.scalatestplus" %% s"scalacheck-$scalaTestScalaCheckVersion" % (scalaTestVersion + ".0") % Test
      }
      val scalatestMockito = Def.setting {
        "org.scalatestplus" %% "mockito-4-11" % (scalaTestVersion + ".0") % Test
      }

      val pojosr = "com.googlecode.pojosr" % "de.kalpatec.pojosr.framework" % "0.2.1" % Test
      val tinybundles = "org.ops4j.pax.tinybundles" % "tinybundles" % "3.0.0" % Test

      // in-memory filesystem for file related tests
      val jimfs = "com.google.jimfs" % "jimfs" % "1.3.0" % Test

      // docker utils
      val dockerClient = ("com.spotify" % "docker-client" % "8.16.0" % Test)
        .exclude("org.bouncycastle", "bcpkix-jdk15on")

      val jackson = Def.setting {
        Seq(
          (jacksonCore.value % Test).force(),
          (jacksonAnnotations.value % Test).force(),
          (jacksonDatabind.value % Test).force(),
          ("com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-base" % jacksonCoreVersion % Test).force(),
          ("com.fasterxml.jackson.jaxrs" % "jackson-jaxrs-json-provider" % jacksonCoreVersion % Test).force(),
          ("com.fasterxml.jackson.datatype" % "jackson-datatype-guava" % jacksonCoreVersion % Test).force())
      }

      // metrics, measurements, perf testing
      val metrics = "io.dropwizard.metrics" % "metrics-core" % "4.2.22" % Test
      val metricsJvm = "io.dropwizard.metrics" % "metrics-jvm" % "4.2.22" % Test
      val latencyUtils = "org.latencyutils" % "LatencyUtils" % "2.0.3" % Test
      val hdrHistogram = "org.hdrhistogram" % "HdrHistogram" % "2.1.12" % Test
      val metricsAll = Seq(metrics, metricsJvm, latencyUtils, hdrHistogram)

      // sigar logging
      val slf4jJul = "org.slf4j" % "jul-to-slf4j" % slf4jVersion % Test
      val slf4jLog4j = "org.slf4j" % "log4j-over-slf4j" % slf4jVersion % Test

      // reactive streams tck
      val reactiveStreamsTck = ("org.reactivestreams" % "reactive-streams-tck" % reactiveStreamsVersion % Test)
        .exclude("org.testng", "testng")

      val protobufRuntime = "com.google.protobuf" % "protobuf-java" % protobufJavaVersion % Test

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
      val junit5 = Compile.junit5 % "optional;provided;test"

      val scalatest = Def.setting { "org.scalatest" %% "scalatest" % scalaTestVersion % "optional;provided;test" }

      val logback = Compile.logback % "optional;provided;test"

      val protobufRuntime = "com.google.protobuf" % "protobuf-java" % protobufJavaVersion % "optional;provided"

    }

  }

  import Compile._
  // TODO check if `l ++=` everywhere expensive?
  val l = libraryDependencies

  val actor = l ++= (CrossVersion.partialVersion(scalaVersion.value) match {
    // java8-compat is only used in a couple of places for 2.13,
    // it is probably possible to remove the dependency if needed.
    case Some((2, n)) if n == 12 =>
      List("org.scala-lang.modules" %% "scala-java8-compat" % java8CompatVersion.value) // Scala License
    case _ => List.empty
  }) ++ Seq(config)

  val actorTyped = l ++= Seq(slf4jApi)

  val discovery = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value)

  val coordination = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value)

  val testkit = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value) ++ TestDependencies.metricsAll

  val actorTests = l ++= Seq(
    TestDependencies.junit,
    TestDependencies.scalatest.value,
    TestDependencies.scalatestJUnit.value,
    TestDependencies.scalatestScalaCheck.value,
    TestDependencies.bcpkix, // to force TestDependencies.dockerClient to use safe version of this lib
    TestDependencies.commonsCodec,
    TestDependencies.commonsCompress, // to force TestDependencies.dockerClient to use safe version of this lib
    TestDependencies.commonsIo, // to force TestDependencies.dockerClient to use safe version of this lib
    TestDependencies.commonsMath,
    TestDependencies.httpClient, // to force TestDependencies.dockerClient to use safe version of this lib
    TestDependencies.jimfs,
    TestDependencies.dockerClient,
    Provided.activation // dockerClient needs javax.activation.DataSource in JDK 11+
  ) ++ {
    // TestDependencies.dockerClient bring in older versions of libs that have CVEs
    TestDependencies.jackson.value
  }

  val actorTestkitTyped = l ++= Seq(
    Provided.logback,
    Provided.junit,
    Provided.junit5,
    Provided.scalatest.value,
    TestDependencies.scalatestJUnit.value)

  val pki = l ++=
    Seq(
      asnOne,
      // pull up slf4j version from the one provided transitively in asnOne to fix unidoc
      Compile.slf4jApi,
      TestDependencies.scalatest.value)

  val remoteDependencies = Seq(`netty-transport`, `netty-handler`, aeronDriver, aeronClient)
  val remoteOptionalDependencies = remoteDependencies.map(_ % "optional")

  val remote = l ++= Seq(
    agrona,
    TestDependencies.junit,
    TestDependencies.scalatest.value,
    TestDependencies.jimfs,
    TestDependencies.protobufRuntime) ++ remoteOptionalDependencies

  val remoteTests = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value) ++ remoteDependencies

  val multiNodeTestkit = l ++= Seq(`netty-transport`, `netty-handler`)

  val cluster = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value, TestDependencies.logback)

  val clusterTools = l ++= Seq(TestDependencies.junit, TestDependencies.scalatest.value)

  val clusterSharding = l ++= Seq(
    Provided.levelDBmultiJVM,
    Provided.levelDBNative,
    TestDependencies.junit,
    TestDependencies.scalatest.value,
    TestDependencies.commonsIo,
    TestDependencies.ycsb)

  val clusterMetrics = l ++= Seq(
    Provided.sigarLoader,
    TestDependencies.slf4jJul,
    TestDependencies.slf4jLog4j,
    TestDependencies.logback,
    TestDependencies.scalatestMockito.value)

  val distributedData = l ++= Seq(lmdb, TestDependencies.junit, TestDependencies.scalatest.value)

  val slf4j = l ++= Seq(slf4jApi, TestDependencies.logback)

  val persistence = l ++= Seq(
    Provided.levelDB,
    Provided.levelDBNative,
    TestDependencies.scalatest.value,
    TestDependencies.scalatestJUnit.value,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    TestDependencies.commonsCodec)

  val persistenceQuery = l ++= Seq(
    TestDependencies.scalatest.value,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    Provided.levelDB,
    Provided.levelDBNative)

  val persistenceTck = l ++= Seq(
    TestDependencies.scalatest.value.withConfigurations(Some("compile")),
    TestDependencies.junit.withConfigurations(Some("compile")),
    Provided.levelDB,
    Provided.levelDBNative)

  val persistenceTestKit = l ++= Seq(TestDependencies.scalatest.value, TestDependencies.logback)

  val persistenceTypedTests = l ++= Seq(TestDependencies.scalatest.value, TestDependencies.logback)

  val persistenceShared = l ++= Seq(Provided.levelDB, Provided.levelDBNative, TestDependencies.logback)

  val jackson = l ++= Seq(
    jacksonCore.value,
    jacksonAnnotations.value,
    jacksonDatabind.value,
    jacksonJdk8.value,
    jacksonJsr310.value,
    jacksonParameterNames.value,
    jacksonCbor.value,
    jacksonScala.value,
    lz4Java,
    TestDependencies.junit,
    TestDependencies.scalatest.value)
  val osgi = l ++= Seq(
    osgiCore,
    osgiCompendium,
    TestDependencies.logback,
    TestDependencies.commonsIo,
    TestDependencies.pojosr,
    TestDependencies.tinybundles,
    TestDependencies.scalatest.value,
    TestDependencies.junit)

  val docs = l ++= Seq(
    TestDependencies.scalatest.value,
    TestDependencies.junit,
    Docs.sprayJson,
    Docs.gson,
    Provided.levelDB)

  val benchJmh = l ++= Seq(logback, Provided.levelDB, Provided.levelDBNative, Compile.jctools)

  // pekko stream

  lazy val stream = l ++= Seq[sbt.ModuleID](reactiveStreams, sslConfigCore.value, TestDependencies.scalatest.value)

  lazy val streamTestkit = l ++= Seq(
    TestDependencies.scalatest.value,
    TestDependencies.scalatestScalaCheck.value,
    TestDependencies.junit)

  lazy val streamTests = l ++= Seq(
    TestDependencies.scalatest.value,
    TestDependencies.scalatestScalaCheck.value,
    TestDependencies.junit,
    TestDependencies.commonsIo,
    TestDependencies.jimfs)

  lazy val streamTestsTck = l ++= Seq(
    TestDependencies.scalatest.value,
    TestDependencies.scalatestTestNG.value,
    TestDependencies.scalatestScalaCheck.value,
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
