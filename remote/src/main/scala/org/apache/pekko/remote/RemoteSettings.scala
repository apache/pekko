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

package org.apache.pekko.remote

import scala.collection.immutable
import scala.concurrent.duration._

import scala.annotation.nowarn
import com.typesafe.config.Config

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.Props
import pekko.annotation.InternalApi
import pekko.event.Logging
import pekko.event.Logging.LogLevel
import pekko.japi.Util._
import pekko.remote.artery.ArterySettings
import pekko.util.Helpers.{ toRootLowerCase, ConfigOps, Requiring }
import pekko.util.Timeout

final class RemoteSettings(val config: Config) {
  import config._

  import pekko.util.ccompat.JavaConverters._

  val Artery = ArterySettings(getConfig("pekko.remote.artery"))

  val WarnAboutDirectUse: Boolean = getBoolean("pekko.remote.warn-about-direct-use")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val LogReceive: Boolean = getBoolean("pekko.remote.classic.log-received-messages")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val LogSend: Boolean = getBoolean("pekko.remote.classic.log-sent-messages")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val LogFrameSizeExceeding: Option[Int] = {
    if (config.getString("pekko.remote.classic.log-frame-size-exceeding").toLowerCase == "off") None
    else Some(getBytes("pekko.remote.classic.log-frame-size-exceeding").toInt)
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val UntrustedMode: Boolean = getBoolean("pekko.remote.classic.untrusted-mode")

  /**
   * INTERNAL API
   */
  @nowarn("msg=deprecated")
  @InternalApi private[pekko] def untrustedMode: Boolean =
    if (Artery.Enabled) Artery.UntrustedMode else UntrustedMode
  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val TrustedSelectionPaths: Set[String] =
    getStringList("pekko.remote.classic.trusted-selection-paths").asScala.toSet

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val RemoteLifecycleEventsLogLevel: LogLevel = toRootLowerCase(
    getString("pekko.remote.classic.log-remote-lifecycle-events")) match {
    case "on" => Logging.DebugLevel
    case other =>
      Logging.levelFor(other) match {
        case Some(level) => level
        case None =>
          throw new ConfigurationException("Logging level must be one of (on, off, debug, info, warning, error)")
      }
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val Dispatcher: String = getString("pekko.remote.classic.use-dispatcher")

  @nowarn("msg=deprecated")
  def configureDispatcher(props: Props): Props =
    if (Artery.Enabled) {
      if (Artery.Advanced.Dispatcher.isEmpty) props else props.withDispatcher(Artery.Advanced.Dispatcher)
    } else {
      if (Dispatcher.isEmpty) props else props.withDispatcher(Dispatcher)
    }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val ShutdownTimeout: Timeout = {
    Timeout(config.getMillisDuration("pekko.remote.classic.shutdown-timeout"))
  }.requiring(_.duration > Duration.Zero, "shutdown-timeout must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val FlushWait: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.flush-wait-on-shutdown")
  }.requiring(_ > Duration.Zero, "flush-wait-on-shutdown must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val StartupTimeout: Timeout = {
    Timeout(config.getMillisDuration("pekko.remote.classic.startup-timeout"))
  }.requiring(_.duration > Duration.Zero, "startup-timeout must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val RetryGateClosedFor: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.retry-gate-closed-for")
  }.requiring(_ >= Duration.Zero, "retry-gate-closed-for must be >= 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val UsePassiveConnections: Boolean = getBoolean("pekko.remote.classic.use-passive-connections")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val BackoffPeriod: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.backoff-interval")
  }.requiring(_ > Duration.Zero, "backoff-interval must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val LogBufferSizeExceeding: Int = {
    val key = "pekko.remote.classic.log-buffer-size-exceeding"
    config.getString(key).toLowerCase match {
      case "off" | "false" => Int.MaxValue
      case _               => config.getInt(key)
    }
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val SysMsgAckTimeout: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.system-message-ack-piggyback-timeout")
  }.requiring(_ > Duration.Zero, "system-message-ack-piggyback-timeout must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val SysResendTimeout: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.resend-interval")
  }.requiring(_ > Duration.Zero, "resend-interval must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val SysResendLimit: Int = {
    config.getInt("pekko.remote.classic.resend-limit")
  }.requiring(_ > 0, "resend-limit must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val SysMsgBufferSize: Int = {
    getInt("pekko.remote.classic.system-message-buffer-size")
  }.requiring(_ > 0, "system-message-buffer-size must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val InitialSysMsgDeliveryTimeout: FiniteDuration = {
    config.getMillisDuration("pekko.remote.classic.initial-system-message-delivery-timeout")
  }.requiring(_ > Duration.Zero, "initial-system-message-delivery-timeout must be > 0")

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val QuarantineSilentSystemTimeout: FiniteDuration = {
    val key = "pekko.remote.classic.quarantine-after-silence"
    config.getString(key).toLowerCase match {
      case "off" | "false" => Duration.Zero
      case _ =>
        config.getMillisDuration(key).requiring(_ > Duration.Zero, "quarantine-after-silence must be > 0")
    }
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val QuarantineDuration: FiniteDuration = {
    config
      .getMillisDuration("pekko.remote.classic.prune-quarantine-marker-after")
      .requiring(_ > Duration.Zero, "prune-quarantine-marker-after must be > 0 ms")
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val CommandAckTimeout: Timeout = {
    Timeout(config.getMillisDuration("pekko.remote.classic.command-ack-timeout"))
  }.requiring(_.duration > Duration.Zero, "command-ack-timeout must be > 0")

  val UseUnsafeRemoteFeaturesWithoutCluster: Boolean = getBoolean(
    "pekko.remote.use-unsafe-remote-features-outside-cluster")

  val WarnUnsafeWatchWithoutCluster: Boolean = getBoolean("pekko.remote.warn-unsafe-watch-outside-cluster")

  val WatchFailureDetectorConfig: Config = getConfig("pekko.remote.watch-failure-detector")
  val WatchFailureDetectorImplementationClass: String = WatchFailureDetectorConfig.getString("implementation-class")
  val WatchHeartBeatInterval: FiniteDuration = {
    WatchFailureDetectorConfig.getMillisDuration("heartbeat-interval")
  }.requiring(_ > Duration.Zero, "watch-failure-detector.heartbeat-interval must be > 0")
  val WatchUnreachableReaperInterval: FiniteDuration = {
    WatchFailureDetectorConfig.getMillisDuration("unreachable-nodes-reaper-interval")
  }.requiring(_ > Duration.Zero, "watch-failure-detector.unreachable-nodes-reaper-interval must be > 0")
  val WatchHeartbeatExpectedResponseAfter: FiniteDuration = {
    WatchFailureDetectorConfig.getMillisDuration("expected-response-after")
  }.requiring(_ > Duration.Zero, "watch-failure-detector.expected-response-after > 0")

  val Transports: immutable.Seq[(String, immutable.Seq[String], Config)] = transportNames.map { name =>
    val transportConfig = transportConfigFor(name)
    (
      transportConfig.getString("transport-class"),
      immutableSeq(transportConfig.getStringList("applied-adapters")).reverse,
      transportConfig)
  }

  @deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
  val Adapters: Map[String, String] = configToMap(getConfig("pekko.remote.classic.adapters"))

  private val AllowableProtocolNames = Set("pekko", "akka")

  val ProtocolName: String = {
    val setting = getString("pekko.remote.protocol-name")
    if (!AllowableProtocolNames.contains(setting)) {
      throw new ConfigurationException("The only allowed values for pekko.remote.protocol-name " +
        "are \"pekko\" and \"akka\".")
    }
    setting
  }

  val AcceptProtocolNames: Set[String] = {
    val set = getStringList("pekko.remote.accept-protocol-names").asScala.toSet
    if (set.isEmpty) {
      throw new ConfigurationException("pekko.remote.accept-protocol-names setting must not be empty. " +
        "The setting is an array and the only acceptable values are \"pekko\" and \"akka\".")
    }
    val filteredSet = set.filterNot(AllowableProtocolNames.contains)
    if (filteredSet.nonEmpty) {
      throw new ConfigurationException("pekko.remote.accept-protocol-names is an array setting " +
        "that only accepts the values \"pekko\" and \"akka\".")
    }
    set
  }

  private def transportNames: immutable.Seq[String] =
    immutableSeq(getStringList("pekko.remote.classic.enabled-transports"))

  private def transportConfigFor(transportName: String): Config = getConfig(transportName)

  private def configToMap(cfg: Config): Map[String, String] =
    cfg.root.unwrapped.asScala.toMap.map { case (k, v) => (k, v.toString) }

}
