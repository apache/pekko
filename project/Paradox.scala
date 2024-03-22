/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import com.lightbend.paradox.sbt.ParadoxPlugin
import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._
import com.lightbend.paradox.apidoc.ApidocPlugin
import com.lightbend.paradox.projectinfo.ParadoxProjectInfoPluginKeys.projectInfoVersion
import org.apache.pekko.PekkoParadoxPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtlicensereport.SbtLicenseReport.autoImportImpl.dumpLicenseReportAggregate

import scala.concurrent.duration._

object Paradox {
  val pekkoBaseURL = "https://pekko.apache.org"
  val propertiesSettings = Seq(
    Compile / paradoxProperties ++= Map(
      "canonical.base_url" -> s"$pekkoBaseURL/docs/pekko/current",
      "github.base_url" -> GitHub
        .url(version.value), // for links like this: @github[#1](#1) or @github[83986f9](83986f9)
      "extref.pekko.http.base_url" -> s"$pekkoBaseURL/docs/pekko-http/current/%s",
      "extref.pekko-management.base_url" -> s"$pekkoBaseURL/docs/pekko-management/current/%s",
      "extref.platform-guide.base_url" -> "https://developer.lightbend.com/docs/akka-platform-guide/%s",
      "extref.wikipedia.base_url" -> "https://en.wikipedia.org/wiki/%s",
      "extref.github.base_url" -> (GitHub.url(version.value) + "/%s"), // for links to our sources
      "extref.samples.base_url" -> s"$pekkoBaseURL/docs/pekko-samples/current/%s",
      "pekko.doc.dns" -> s"$pekkoBaseURL",
      "scaladoc.pekko.base_url" -> s"$pekkoBaseURL/api/pekko/${projectInfoVersion.value}/org/apache",
      "scaladoc.pekko.http.base_url" -> s"$pekkoBaseURL/api/pekko-http/current/org/apache",
      "scaladoc.org.apache.pekko.base_url" -> s"$pekkoBaseURL/api/pekko/${projectInfoVersion.value}",
      "scaladoc.org.apache.pekko.http.base_url" -> s"$pekkoBaseURL/api/pekko-http/current",
      "javadoc.java.base_url" -> "https://docs.oracle.com/en/java/javase/11/docs/api/java.base/",
      "javadoc.java.link_style" -> "direct",
      "javadoc.pekko.base_url" -> s"$pekkoBaseURL/japi/pekko/${projectInfoVersion.value}/org/apache",
      "javadoc.pekko.link_style" -> "direct",
      "javadoc.pekko.http.base_url" -> s"$pekkoBaseURL/japi/pekko-http/current/org/apache",
      "javadoc.pekko.http.link_style" -> "frames",
      "javadoc.org.apache.pekko.base_url" -> s"$pekkoBaseURL/japi/pekko/${projectInfoVersion.value}",
      "javadoc.org.apache.pekko.link_style" -> "direct",
      "javadoc.org.apache.pekko.http.base_url" -> s"$pekkoBaseURL/japi/pekko-http/current",
      "javadoc.org.apache.pekko.http.link_style" -> "frames",
      "javadoc.com.fasterxml.jackson.annotation.base_url" -> "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-annotations/latest/",
      "javadoc.com.fasterxml.jackson.annotation.link_style" -> "direct",
      "javadoc.com.fasterxml.jackson.databind.base_url" -> "https://javadoc.io/doc/com.fasterxml.jackson.core/jackson-databind/latest/",
      "javadoc.com.fasterxml.jackson.databind.link_style" -> "direct",
      "javadoc.com.google.protobuf.base_url" -> "https://javadoc.io/doc/com.google.protobuf/protobuf-java/latest/",
      "javadoc.com.google.protobuf.link_style" -> "direct",
      "javadoc.com.typesafe.config.base_url" -> "https://javadoc.io/doc/com.typesafe/config/latest/",
      "javadoc.com.typesafe.config.link_style" -> "direct",
      "javadoc.org.slf4j.base_url" -> "https://www.javadoc.io/doc/org.slf4j/slf4j-api/latest/org.slf4j",
      "javadoc.org.slf4j.link_style" -> "direct",
      "scala.version" -> scalaVersion.value,
      "scala.binary.version" -> scalaBinaryVersion.value,
      "pekko.version" -> version.value,
      "scalatest.version" -> Dependencies.scalaTestVersion,
      "sigar_loader.version" -> "1.6.6-rev002",
      "aeron_version" -> Dependencies.aeronVersion,
      "netty_version" -> Dependencies.nettyVersion,
      "logback_version" -> Dependencies.logbackVersion))

  val rootsSettings = Seq(
    paradoxRoots := List(
      "index.html",
      // TODO page not linked to
      "fault-tolerance-sample.html"))

  lazy val themeSettings = Seq(
    pekkoParadoxGithub := Some("https://github.com/apache/pekko"))

  // FIXME https://github.com/lightbend/paradox/issues/350
  // Exclusions from direct compilation for includes dirs/files not belonging in a TOC
  val includesSettings = Seq(
    (Compile / paradoxMarkdownToHtml / excludeFilter) := (Compile / paradoxMarkdownToHtml / excludeFilter).value ||
    ParadoxPlugin.InDirectoryFilter((Compile / paradox / sourceDirectory).value / "includes"),
    // Links are interpreted relative to the page the snippet is included in,
    // instead of relative to the place where the snippet is declared.
    (Compile / paradoxMarkdownToHtml / excludeFilter) := (Compile / paradoxMarkdownToHtml / excludeFilter).value ||
    ParadoxPlugin.InDirectoryFilter((Compile / paradox / sourceDirectory).value / "includes.html"))

  val groupsSettings = Seq(Compile / paradoxGroups := Map("Language" -> Seq("Scala", "Java")))

  val parsingSettings = Seq(Compile / paradoxParsingTimeout := 5.seconds)

  val sourceGeneratorSettings = Seq(
    Compile / paradoxMarkdownToHtml / sourceGenerators += Def.taskDyn {
      val targetFile = (Compile / paradox / sourceManaged).value / "project" / "license-report.md"

      (LocalRootProject / dumpLicenseReportAggregate).map { dir =>
        IO.copy(List(dir / "pekko-root-licenses.md" -> targetFile)).toList
      }
    }.taskValue)

  val settings =
    propertiesSettings ++
    rootsSettings ++
    includesSettings ++
    groupsSettings ++
    parsingSettings ++
    themeSettings ++
    sourceGeneratorSettings ++
    Seq(
      Compile / paradox / name := "Pekko",
      ApidocPlugin.autoImport.apidocRootPackage := "org.apache.pekko")
}
