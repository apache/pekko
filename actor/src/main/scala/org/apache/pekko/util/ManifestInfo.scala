/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.Manifest

import scala.annotation.nowarn
import scala.collection.immutable

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.event.Logging

/**
 * Apache Pekko extension that extracts [[ManifestInfo.Version]] information from META-INF/MANIFEST.MF in jar files
 * on the classpath of the `ClassLoader` of the `ActorSystem`.
 */
object ManifestInfo extends ExtensionId[ManifestInfo] with ExtensionIdProvider {
  private val ImplTitle = "Implementation-Title"
  private val ImplVersion = "Implementation-Version"
  private val ImplVendor = "Implementation-Vendor-Id"

  private val BundleName = "Bundle-Name"
  private val BundleVersion = "Bundle-Version"
  private val BundleVendor = "Bundle-Vendor"

  private val knownVendors = Set("org.apache.pekko")

  override def get(system: ActorSystem): ManifestInfo = super.get(system)
  override def get(system: ClassicActorSystemProvider): ManifestInfo = super.get(system)

  override def lookup: ManifestInfo.type = ManifestInfo

  override def createExtension(system: ExtendedActorSystem): ManifestInfo = new ManifestInfo(system)

  /**
   * Comparable version information
   */
  final class Version(val version: String) extends Comparable[Version] {
    private val impl = new pekko.util.Version(version)

    override def compareTo(other: Version): Int =
      impl.compareTo(other.impl)

    override def equals(o: Any): Boolean = o match {
      case v: Version => impl.equals(v.impl)
      case _          => false
    }

    override def hashCode(): Int =
      impl.hashCode()

    override def toString: String =
      impl.toString
  }

  /** INTERNAL API */
  private[util] def checkSameVersion(
      productName: String,
      dependencies: immutable.Seq[String],
      versions: Map[String, Version]): Option[String] = {
    @nowarn("msg=deprecated")
    val filteredVersions = versions.filterKeys(dependencies.toSet)
    val values = filteredVersions.values.toSet
    if (values.size > 1) {
      val highestVersion = values.max
      val toBeUpdated = filteredVersions.collect { case (k, v) if v != highestVersion => s"$k" }.mkString(", ")
      val groupedByVersion = filteredVersions.toSeq
        .groupBy { case (_, v) => v }
        .toSeq
        .sortBy(_._1)
        .map { case (k, v) => k -> v.map(_._1).sorted.mkString("[", ", ", "]") }
        .map { case (k, v) => s"($k, $v)" }
        .mkString(", ")
      Some(
        s"You are using version $highestVersion of $productName, but it appears " +
        s"you (perhaps indirectly) also depend on older versions of related artifacts. " +
        s"You can solve this by adding an explicit dependency on version $highestVersion " +
        s"of the [$toBeUpdated] artifacts to your project. " +
        s"Here's a complete collection of detected artifacts: $groupedByVersion. " +
        "See also: https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed")
    } else None
  }
}

/**
 * Utility that extracts [[ManifestInfo#Version]] information from META-INF/MANIFEST.MF in jar files on the classpath.
 * Note that versions can only be found in ordinary jar files, for example not in "fat jars' assembled from
 * many jar files.
 */
final class ManifestInfo(val system: ExtendedActorSystem) extends Extension {
  import ManifestInfo._

  /**
   * Versions of artifacts from known vendors.
   */
  val versions: Map[String, Version] = {

    var manifests = Map.empty[String, Version]

    try {
      val resources = system.dynamicAccess.classLoader.getResources("META-INF/MANIFEST.MF")
      while (resources.hasMoreElements()) {
        val ios = resources.nextElement().openStream()
        try {
          val manifest = new Manifest(ios)
          val attributes = manifest.getMainAttributes
          val title = attributes.getValue(new Attributes.Name(ImplTitle)) match {
            case null => attributes.getValue(new Attributes.Name(BundleName))
            case t    => t
          }
          val version = attributes.getValue(new Attributes.Name(ImplVersion)) match {
            case null => attributes.getValue(new Attributes.Name(BundleVersion))
            case v    => v
          }
          val vendor = attributes.getValue(new Attributes.Name(ImplVendor)) match {
            case null => attributes.getValue(new Attributes.Name(BundleVendor))
            case v    => v
          }

          if (title != null
            && version != null
            && vendor != null
            && knownVendors(vendor)) {
            manifests = manifests.updated(title, new Version(version))
          }
        } finally {
          ios.close()
        }
      }
    } catch {
      case ioe: IOException =>
        Logging(system, classOf[ManifestInfo]).warning("Could not read manifest information. {}", ioe)
    }
    manifests
  }

  /**
   * Verify that the version is the same for all given artifacts.
   *
   * If configuration `pekko.fail-mixed-versions=on` it will throw an `IllegalStateException` if the
   * versions are not the same for all given artifacts.
   *
   * @return `true` if versions are the same
   */
  def checkSameVersion(productName: String, dependencies: immutable.Seq[String], logWarning: Boolean): Boolean = {
    checkSameVersion(productName, dependencies, logWarning, throwException = system.settings.FailMixedVersions)
  }

  /**
   * Verify that the version is the same for all given artifacts.
   *
   * If `throwException` is `true` it will throw an `IllegalStateException` if the versions are not the same
   * for all given artifacts.
   *
   * @return `true` if versions are the same
   */
  def checkSameVersion(
      productName: String,
      dependencies: immutable.Seq[String],
      logWarning: Boolean,
      throwException: Boolean): Boolean = {
    ManifestInfo.checkSameVersion(productName, dependencies, versions) match {
      case Some(message) =>
        if (logWarning)
          Logging(system, classOf[ManifestInfo]).warning(message)

        if (throwException)
          throw new IllegalStateException(message)
        else
          false
      case None => true
    }
  }
}
