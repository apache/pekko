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

package org.apache.pekko.cluster

import java.io.File
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import scala.annotation.tailrec
import scala.util.control.NonFatal

import io.aeron.CommonContext
import io.aeron.driver.MediaDriver
import io.aeron.driver.ThreadingMode
import org.agrona.IoUtil

import org.apache.pekko
import pekko.remote.RemoteSettings
import pekko.remote.artery.ArterySettings
import pekko.remote.artery.ArterySettings.AeronUpd
import pekko.remote.artery.aeron.TaskRunner
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec

import com.typesafe.config.ConfigFactory

object SharedMediaDriverSupport {

  private val mediaDriver = new AtomicReference[Option[MediaDriver]](None)

  def loadArterySettings(config: MultiNodeConfig): ArterySettings =
    new RemoteSettings(ConfigFactory.load(config.config)).Artery

  def startMediaDriver(config: MultiNodeConfig): Unit = {
    val arterySettings = loadArterySettings(config)
    if (arterySettings.Enabled && arterySettings.Transport == AeronUpd) {
      val aeronDir = arterySettings.Advanced.Aeron.AeronDirectoryName
      require(aeronDir.nonEmpty, "aeron-dir must be defined")

      // Check if the media driver is already started by another multi-node jvm.
      // It checks more than one time with a sleep in-between. The number of checks
      // depends on the multi-node index (i).
      @tailrec def isDriverInactive(i: Int): Boolean = {
        if (i < 0) true
        else {
          val active =
            try CommonContext.isDriverActive(new File(aeronDir), 5000,
                new Consumer[String] {
                  override def accept(msg: String): Unit = {
                    println(msg)
                  }
                })
            catch {
              case NonFatal(e) =>
                println("Exception checking isDriverActive: " + e.getMessage)
                false
            }
          if (active) false
          else {
            Thread.sleep(500)
            isDriverInactive(i - 1)
          }
        }
      }

      try {
        if (isDriverInactive(MultiNodeSpec.selfIndex)) {
          val driverContext = new MediaDriver.Context
          driverContext.aeronDirectoryName(aeronDir)
          driverContext.clientLivenessTimeoutNs(arterySettings.Advanced.Aeron.ClientLivenessTimeout.toNanos)
          driverContext.publicationUnblockTimeoutNs(arterySettings.Advanced.Aeron.PublicationUnblockTimeout.toNanos)
          driverContext.imageLivenessTimeoutNs(arterySettings.Advanced.Aeron.ImageLivenessTimeout.toNanos)
          driverContext.driverTimeoutMs(arterySettings.Advanced.Aeron.DriverTimeout.toMillis)
          val idleCpuLevel = arterySettings.Advanced.Aeron.IdleCpuLevel
          driverContext
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(TaskRunner.createIdleStrategy(idleCpuLevel))

          val driver = MediaDriver.launchEmbedded(driverContext)
          println(s"Started media driver in directory [${driver.aeronDirectoryName}]")
          if (!mediaDriver.compareAndSet(None, Some(driver))) {
            throw new IllegalStateException("media driver started more than once")
          }
        }
      } catch {
        case NonFatal(e) =>
          println(s"Failed to start media driver in [$aeronDir]: ${e.getMessage}")
      }
    }
  }

  def isMediaDriverRunningByThisNode: Boolean = mediaDriver.get.isDefined

  def stopMediaDriver(config: MultiNodeConfig): Unit = {
    val maybeDriver = mediaDriver.getAndSet(None)
    maybeDriver.foreach { driver =>
      val arterySettings = loadArterySettings(config)

      // let other nodes shutdown first
      Thread.sleep(5000)

      driver.close()

      try {
        if (arterySettings.Advanced.Aeron.DeleteAeronDirectory) {
          IoUtil.delete(new File(driver.aeronDirectoryName), false)
        }
      } catch {
        case NonFatal(e) =>
          println(
            s"Couldn't delete Aeron embedded media driver files in [${driver.aeronDirectoryName}] " +
            s"due to [${e.getMessage}]")
      }
    }
  }

}
