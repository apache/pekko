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

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.{ ActorSystem, Address, BootstrapSetup, RootActorPath }
import pekko.actor.setup.ActorSystemSetup
import pekko.remote.RARP
import pekko.testkit.{ PekkoSpec, SocketUtil }

import org.scalatest.{ Outcome, Pending }

import com.typesafe.config.{ Config, ConfigFactory }

/**
 * Base class for remoting tests what needs to test interaction between a "local" actor system
 * which is always created (the usual PekkoSpec system), and multiple additional actor systems over artery
 */
abstract class ArteryMultiNodeSpec(config: Config)
    extends PekkoSpec(config.withFallback(ArterySpecSupport.defaultConfig)) {

  def this() = this(ConfigFactory.empty())
  def this(extraConfig: String) = this(ConfigFactory.parseString(extraConfig))

  /** just an alias to make tests more readable */
  def localSystem = system
  def localPort = port(localSystem)
  def port(system: ActorSystem): Int = RARP(system).provider.getDefaultAddress.port.get
  def address(sys: ActorSystem): Address = RARP(sys).provider.getDefaultAddress
  def rootActorPath(sys: ActorSystem) = RootActorPath(address(sys))
  def nextGeneratedSystemName = s"${localSystem.name}-remote-${remoteSystems.size}"
  def freePort(): Int = {
    val udp = ArteryMultiNodeSpec.arteryUdpEnabled(system.settings.config)
    (address(system).host match {
      case Some(host) => SocketUtil.temporaryServerAddress(host, udp)
      case None       => SocketUtil.temporaryServerAddress(udp = udp)
    }).getPort
  }

  private var remoteSystems: Vector[ActorSystem] = Vector.empty

  override protected def withFixture(test: NoArgTest): Outcome = {
    // note that withFixture is also used in FlightRecorderSpecIntegration
    if (!RARP(system).provider.remoteSettings.Artery.Enabled) {
      info(s"${getClass.getName} is only enabled for Artery")
      Pending
    } else
      super.withFixture(test)
  }

  /**
   * @return A new actor system configured with artery enabled. The system will
   *         automatically be terminated after test is completed to avoid leaks.
   */
  def newRemoteSystem(
      extraConfig: Option[String] = None,
      name: Option[String] = None,
      setup: Option[ActorSystemSetup] = None): ActorSystem = {
    val config =
      extraConfig.fold(localSystem.settings.config)(str =>
        ConfigFactory.parseString(str).withFallback(localSystem.settings.config))
    val sysName = name.getOrElse(nextGeneratedSystemName)

    val remoteSystem = setup match {
      case None    => ActorSystem(sysName, config)
      case Some(s) => ActorSystem(sysName, s.and(BootstrapSetup.apply(config)))
    }

    remoteSystems = remoteSystems :+ remoteSystem

    remoteSystem
  }

  override def afterTermination(): Unit = {
    remoteSystems.foreach(sys => shutdown(sys))
    remoteSystems = Vector.empty
    super.afterTermination()
  }

  def arteryTcpTlsEnabled(system: ActorSystem = system): Boolean = {
    val arterySettings = ArterySettings(system.settings.config.getConfig("pekko.remote.artery"))
    arterySettings.Enabled && arterySettings.Transport == ArterySettings.TlsTcp
  }
}

object ArteryMultiNodeSpec {
  def arteryUdpEnabled(systemConfig: Config): Boolean = {
    val arterySettings = ArterySettings(systemConfig.getConfig("pekko.remote.artery"))
    arterySettings.Transport == ArterySettings.AeronUpd
  }

  def freePort(systemConfig: Config): Int = {
    SocketUtil.temporaryLocalPort(ArteryMultiNodeSpec.arteryUdpEnabled(systemConfig))
  }
}
