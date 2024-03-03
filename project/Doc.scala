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

import sbt._
import sbtunidoc.BaseUnidocPlugin.autoImport.{ unidoc, unidocAllSources, unidocProjectFilter }
import sbtunidoc.JavaUnidocPlugin.autoImport.JavaUnidoc
import sbtunidoc.ScalaUnidocPlugin.autoImport.ScalaUnidoc
import sbtunidoc.GenJavadocPlugin.autoImport._
import sbt.Keys._
import sbt.File
import scala.annotation.tailrec

import sbt.ScopeFilter.ProjectFilter

object Scaladoc extends AutoPlugin {

  object CliOptions {
    val scaladocDiagramsEnabled = CliOption("pekko.scaladoc.diagrams", true)
    val scaladocAutoAPI = CliOption("pekko.scaladoc.autoapi", true)
  }

  override def trigger = allRequirements
  override def requires = plugins.JvmPlugin

  val validateDiagrams = settingKey[Boolean]("Validate generated scaladoc diagrams")

  override lazy val projectSettings = {
    inTask(doc)(
      Seq(
        Compile / scalacOptions ++= scaladocOptions(version.value, (ThisBuild / baseDirectory).value),
        // -release caused build failures when generating javadoc:
        Compile / scalacOptions --= Seq("-release", "8"),
        autoAPIMappings := CliOptions.scaladocAutoAPI.get)) ++
    Seq(Compile / validateDiagrams := true) ++
    CliOptions.scaladocDiagramsEnabled.ifTrue(Compile / doc := {
      val docs = (Compile / doc).value
      if ((Compile / validateDiagrams).value)
        scaladocVerifier(docs)
      docs
    })
  }

  def scaladocOptions(ver: String, base: File): List[String] = {
    val urlString = GitHub.url(ver) + "/€{FILE_PATH_EXT}#L€{FILE_LINE}"
    val opts = List(
      "-implicits",
      "-groups",
      "-doc-source-url",
      urlString,
      "-sourcepath",
      base.getAbsolutePath,
      "-doc-title",
      "Apache Pekko",
      "-doc-version",
      ver,
      "-doc-canonical-base-url",
      "https://pekko.apache.org/api/pekko/current/")
    CliOptions.scaladocDiagramsEnabled.ifTrue("-diagrams").toList ::: opts
  }

  def scaladocVerifier(file: File): File = {
    @tailrec
    def findHTMLFileWithDiagram(dirs: Seq[File]): Boolean = {
      if (dirs.isEmpty) false
      else {
        val curr = dirs.head
        val (newDirs, files) = curr.listFiles.partition(_.isDirectory)
        val rest = dirs.tail ++ newDirs
        val hasDiagram = files.exists { f =>
          val name = f.getName
          if (name.endsWith(".html") && !name.startsWith("index-") &&
            !name.equals("index.html") && !name.equals("package.html")) {
            val source = scala.io.Source.fromFile(f)(scala.io.Codec.UTF8)
            val hd =
              try source
                  .getLines()
                  .exists(lines =>
                    lines.contains(
                      "<div class=\"toggleContainer block diagram-container\" id=\"inheritance-diagram-container\">") ||
                    lines.contains("<svg id=\"graph"))
              catch {
                case e: Exception =>
                  throw new IllegalStateException("Scaladoc verification failed for file '" + f + "'", e)
              } finally source.close()
            hd
          } else false
        }
        hasDiagram || findHTMLFileWithDiagram(rest)
      }
    }

    // if we have generated scaladoc and none of the files have a diagram then fail
    if (file.exists() && !findHTMLFileWithDiagram(List(file)))
      sys.error("ScalaDoc diagrams not generated!")
    else
      file
  }
}

/**
 * For projects with few (one) classes there might not be any diagrams.
 */
object ScaladocNoVerificationOfDiagrams extends AutoPlugin {

  override def trigger = noTrigger
  override def requires = Scaladoc

  override lazy val projectSettings = Seq(Compile / Scaladoc.validateDiagrams := false)
}

/**
 * Unidoc settings for root project. Adds unidoc command.
 */
object UnidocRoot extends AutoPlugin {

  object CliOptions {
    val genjavadocEnabled = CliOption("pekko.genjavadoc.enabled", false)
  }

  object autoImport {
    val unidocRootIgnoreProjects = settingKey[Seq[ProjectReference]]("Projects to ignore when generating unidoc")
  }
  import autoImport._

  override def trigger = noTrigger
  override def requires =
    UnidocRoot.CliOptions.genjavadocEnabled
      .ifTrue(sbtunidoc.ScalaUnidocPlugin && sbtunidoc.JavaUnidocPlugin && sbtunidoc.GenJavadocPlugin)
      .getOrElse(sbtunidoc.ScalaUnidocPlugin)

  val pekkoSettings = UnidocRoot.CliOptions.genjavadocEnabled
    .ifTrue(Seq(
      JavaUnidoc / unidoc / javacOptions := {
        if (JdkOptions.isJdk8) Seq("-Xdoclint:none")
        else Seq("-Xdoclint:none", "--ignore-source-errors", "--no-module-directories")
      }))
    .getOrElse(Nil)

  override lazy val projectSettings = {
    def unidocRootProjectFilter(ignoreProjects: Seq[ProjectReference]): ProjectFilter =
      ignoreProjects.foldLeft(inAnyProject) { _ -- inProjects(_) }

    inTask(unidoc)(
      Seq(
        ScalaUnidoc / unidocProjectFilter := unidocRootProjectFilter(unidocRootIgnoreProjects.value),
        JavaUnidoc / unidocProjectFilter := unidocRootProjectFilter(unidocRootIgnoreProjects.value),
        Compile / doc / apiMappings ++= {
          val entries: Seq[Attributed[File]] = (LocalProject("slf4j") / Compile / fullClasspath).value

          def mappingsFor(organization: String, names: List[String], location: String,
              revision: String => String = identity): Seq[(File, URL)] = {
            for {
              entry: Attributed[File] <- entries
              module: ModuleID <- entry.get(moduleID.key)
              if module.organization == organization
              if names.exists(module.name.startsWith)
            } yield entry.data -> url(location.format(module.revision))
          }

          val mappings: Seq[(File, URL)] =
            mappingsFor("org.slf4j", List("slf4j-api"), "https://www.javadoc.io/doc/org.slf4j/slf4j-api/%s/")

          mappings.toMap
        },
        ScalaUnidoc / apiMappings := (Compile / doc / apiMappings).value) ++
      UnidocRoot.CliOptions.genjavadocEnabled
        .ifTrue(Seq(JavaUnidoc / unidocAllSources ~= { v =>
          v.map(
            _.filterNot(s =>
              // org.apache.pekko.stream.scaladsl.GraphDSL.Implicits.ReversePortsOps
              // contains code that genjavadoc turns into (probably
              // incorrect) Java code that in turn confuses the javadoc
              // tool.
              s.getAbsolutePath.endsWith("scaladsl/GraphDSL.java") ||
              // Since adding -P:genjavadoc:strictVisibility=true,
              // the javadoc tool would NullPointerException while
              // determining the upper bound for some generics:
              s.getAbsolutePath.endsWith("TopicImpl.java") ||
              s.getAbsolutePath.endsWith("PersistencePlugin.java") ||
              s.getAbsolutePath.endsWith("GraphDelegate.java") ||
              s.getAbsolutePath.contains("/impl/")))
        }))
        .getOrElse(Nil))
  }
}

/**
 * Unidoc settings for every multi-project. Adds genjavadoc specific settings.
 */
object BootstrapGenjavadoc extends AutoPlugin {

  override def trigger = allRequirements
  override def requires =
    UnidocRoot.CliOptions.genjavadocEnabled
      .ifTrue {
        // require 11, fail fast for 8, 9, 10
        require(JdkOptions.isJdk11orHigher, "Javadoc generation requires at least jdk 11")
        sbtunidoc.GenJavadocPlugin
      }
      .getOrElse(plugins.JvmPlugin)

  override lazy val projectSettings = UnidocRoot.CliOptions.genjavadocEnabled
    .ifTrue(Seq(
      unidocGenjavadocVersion := "0.19",
      Compile / scalacOptions ++= Seq(
        "-P:genjavadoc:fabricateParams=false",
        "-P:genjavadoc:suppressSynthetic=false",
        "-P:genjavadoc:strictVisibility=true")))
    .getOrElse(Nil)
}
