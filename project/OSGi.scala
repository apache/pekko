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

import com.github.sbt.osgi.OsgiKeys
import com.github.sbt.osgi.SbtOsgi._
import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin
import sbt.Keys._
import sbt._

object OSGi {

  // The included osgiSettings that creates bundles also publish the jar files
  // in the .../bundles directory which makes testing locally published artifacts
  // a pain. Create bundles but publish them to the normal .../jars directory.
  lazy val osgiSettings =
    defaultOsgiSettings ++ Seq(
      Compile / packageBin := {
        val bundle = OsgiKeys.bundle.value
        // This normally happens automatically when loading the
        // sbt-reproducible-builds plugin, but because we replace
        // `packageBin` wholesale here we need to invoke the post-processing
        // manually. See also
        // https://github.com/raboof/sbt-reproducible-builds#sbt-osgi
        ReproducibleBuildsPlugin.postProcessJar(bundle)
      },
      // This will fail the build instead of accidentally removing classes from the resulting artifact.
      // Each package contained in a project MUST be known to be private or exported, if it's undecided we MUST resolve this
      OsgiKeys.failOnUndecidedPackage := true,
      // By default an entry is generated from module group-id, but our modules do not adhere to such package naming
      OsgiKeys.privatePackage := Seq(),
      // Explicitly specify the version of JavaSE required #23795 (rather depend on
      // figuring that out from the JDK it was built with)
      OsgiKeys.requireCapability := "osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version>=1.8))\"",
      // Recent versions of BND create corrupted jars so use JDK jar instead, see https://github.com/sbt/sbt-osgi/pull/81
      OsgiKeys.packageWithJVMJar := true,
      OsgiKeys.cacheStrategy := Some(OsgiKeys.CacheStrategy.Hash)
    )

  lazy val actor = osgiSettings ++ Seq(
    OsgiKeys.exportPackage := Seq("org.apache.pekko*"),
    OsgiKeys.privatePackage := Seq("org.apache.pekko.osgi.impl"),
    // pekko-actor packages are not imported, as contained in the CP
    OsgiKeys.importPackage := (osgiOptionalImports.map(optionalResolution)) ++ Seq(
      "!sun.misc",
      scalaJava8CompatImport(),
      scalaVersion(scalaImport).value,
      configImport(),
      "*"),
    // dynamicImportPackage needed for loading classes defined in configuration
    OsgiKeys.dynamicImportPackage := Seq("*"))

  lazy val actorTyped = exports(Seq("org.apache.pekko.actor.typed.*"))

  lazy val cluster = exports(Seq("org.apache.pekko.cluster.*"))

  lazy val clusterTools = exports(Seq("org.apache.pekko.cluster.singleton.*", "org.apache.pekko.cluster.client.*",
    "org.apache.pekko.cluster.pubsub.*"))

  lazy val clusterSharding = exports(Seq("org.apache.pekko.cluster.sharding.*"))

  lazy val clusterMetrics =
    exports(Seq("org.apache.pekko.cluster.metrics.*"), imports = Seq(kamonImport(), sigarImport()))

  lazy val distributedData = exports(Seq("org.apache.pekko.cluster.ddata.*"))

  lazy val osgi = exports(Seq("org.apache.pekko.osgi.*"))

  lazy val protobufV3 = osgiSettings ++ Seq(
    OsgiKeys.importPackage := Seq(
      "!sun.misc",
      scalaJava8CompatImport(),
      scalaVersion(scalaImport).value,
      configImport(),
      "*"),
    OsgiKeys.exportPackage := Seq("org.apache.pekko.protobufv3.internal.*"),
    OsgiKeys.privatePackage := Seq("google.protobuf.*"))

  lazy val jackson = exports(Seq("org.apache.pekko.serialization.jackson.*"))

  lazy val remote = exports(Seq("org.apache.pekko.remote.*"))

  lazy val stream =
    exports(
      packages = Seq("org.apache.pekko.stream.*", "com.typesafe.sslconfig.pekko.*"),
      imports = Seq(
        scalaJava8CompatImport(),
        scalaParsingCombinatorImport(),
        sslConfigCoreImport("com.typesafe.sslconfig.ssl.*"),
        sslConfigCoreImport("com.typesafe.sslconfig.util.*"),
        "!com.typesafe.sslconfig.pekko.*"))

  lazy val streamTestkit = exports(Seq("org.apache.pekko.stream.testkit.*"))

  lazy val slf4j = exports(Seq("org.apache.pekko.event.slf4j.*"))

  lazy val persistence = exports(
    Seq("org.apache.pekko.persistence.*"),
    imports = Seq(optionalResolution("org.fusesource.leveldbjni.*"), optionalResolution("org.iq80.leveldb.*")))

  lazy val persistenceTyped = exports(Seq("org.apache.pekko.persistence.typed.*"))

  lazy val persistenceQuery = exports(Seq("org.apache.pekko.persistence.query.*"))

  lazy val testkit = exports(Seq("org.apache.pekko.testkit.*"))

  lazy val discovery = exports(Seq("org.apache.pekko.discovery.*"))

  lazy val coordination = exports(Seq("org.apache.pekko.coordination.*"))

  lazy val osgiOptionalImports = Seq(
    // needed because testkit is normally not used in the application bundle,
    // but it should still be included as transitive dependency and used by BundleDelegatingClassLoader
    // to be able to find reference.conf
    "org.apache.pekko.testkit")

  def exports(packages: Seq[String] = Seq(), imports: Seq[String] = Nil) =
    osgiSettings ++ Seq(
      OsgiKeys.importPackage := imports ++ scalaVersion(defaultImports).value,
      OsgiKeys.exportPackage := packages)
  def defaultImports(scalaVersion: String) =
    Seq(
      "!sun.misc",
      pekkoImport(),
      configImport(),
      "!scala.compat.java8.*",
      "!scala.util.parsing.*",
      scalaImport(scalaVersion),
      "*")
  def pekkoImport(packageName: String = "org.apache.pekko.*") = versionedImport(packageName, "1.0", "1.1")
  def configImport(packageName: String = "com.typesafe.config.*") = versionedImport(packageName, "1.4.0", "1.5.0")
  def scalaImport(version: String) = {
    val packageName = "scala.*"
    val ScalaVersion = """(\d+)\.(\d+)\..*""".r
    val ScalaVersion(epoch, major) = version
    versionedImport(packageName, s"$epoch.$major", s"$epoch.${major.toInt + 1}")
  }
  def scalaJava8CompatImport(packageName: String = "scala.compat.java8.*") =
    versionedImport(packageName, "1.0.2", "1.0.2")
  def scalaParsingCombinatorImport(packageName: String = "scala.util.parsing.combinator.*") =
    versionedImport(packageName, "1.1.0", "1.2.0")
  def sslConfigCoreImport(packageName: String = "com.typesafe.sslconfig") =
    versionedImport(packageName, "0.4.0", "1.0.0")
  def sslConfigCoreSslImport(packageName: String = "com.typesafe.sslconfig.ssl.*") =
    versionedImport(packageName, "0.4.0", "1.0.0")
  def sslConfigCoreUtilImport(packageName: String = "com.typesafe.sslconfig.util.*") =
    versionedImport(packageName, "0.4.0", "1.0.0")
  def kamonImport(packageName: String = "kamon.sigar.*") =
    optionalResolution(versionedImport(packageName, "1.6.5", "1.6.6"))
  def sigarImport(packageName: String = "org.hyperic.*") =
    optionalResolution(versionedImport(packageName, "1.6.5", "1.6.6"))
  def optionalResolution(packageName: String) = "%s;resolution:=optional".format(packageName)
  def versionedImport(packageName: String, lower: String, upper: String) = s"""$packageName;version="[$lower,$upper)""""
}
