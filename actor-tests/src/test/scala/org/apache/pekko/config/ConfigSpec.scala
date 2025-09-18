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

package org.apache.pekko.config

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.event.DefaultLoggingFilter
import pekko.event.Logging.DefaultLogger
import pekko.testkit.PekkoSpec

import org.scalatest.Assertions

import com.typesafe.config.ConfigFactory

class ConfigSpec extends PekkoSpec(ConfigFactory.defaultReference(ActorSystem.findClassLoader())) with Assertions {

  "The default configuration file (i.e. reference.conf)" must {
    "contain all configuration properties for pekko-actor that are used in code with their correct defaults" in {

      val settings = system.settings
      val config = settings.config

      {
        import config._

        getString("pekko.version") should ===(ActorSystem.Version)
        settings.ConfigVersion should ===(ActorSystem.Version)

        getBoolean("pekko.daemonic") should ===(false)

        getBoolean("pekko.actor.serialize-messages") should ===(false)
        settings.SerializeAllMessages should ===(false)

        settings.NoSerializationVerificationNeededClassPrefix should ===(Set("org.apache.pekko."))

        getInt("pekko.scheduler.ticks-per-wheel") should ===(512)
        getDuration("pekko.scheduler.tick-duration", TimeUnit.MILLISECONDS) should ===(10L)
        getBoolean("pekko.scheduler.error-on-tick-duration-verification-failed") should ===(true)
        getString("pekko.scheduler.implementation") should ===("org.apache.pekko.actor.LightArrayRevolverScheduler")

        getBoolean("pekko.daemonic") should ===(false)
        settings.Daemonicity should ===(false)

        getBoolean("pekko.jvm-exit-on-fatal-error") should ===(true)
        settings.JvmExitOnFatalError should ===(true)
        settings.JvmShutdownHooks should ===(true)

        getBoolean("pekko.fail-mixed-versions") should ===(true)
        settings.FailMixedVersions should ===(true)

        getInt("pekko.actor.deployment.default.virtual-nodes-factor") should ===(10)
        settings.DefaultVirtualNodesFactor should ===(10)

        getDuration("pekko.actor.unstarted-push-timeout", TimeUnit.MILLISECONDS) should ===(10.seconds.toMillis)
        settings.UnstartedPushTimeout.duration should ===(10.seconds)

        settings.Loggers.size should ===(1)
        settings.Loggers.head should ===(classOf[DefaultLogger].getName)
        getStringList("pekko.loggers").get(0) should ===(classOf[DefaultLogger].getName)

        getDuration("pekko.logger-startup-timeout", TimeUnit.MILLISECONDS) should ===(5.seconds.toMillis)
        settings.LoggerStartTimeout.duration should ===(5.seconds)

        getString("pekko.logging-filter") should ===(classOf[DefaultLoggingFilter].getName)

        getInt("pekko.log-dead-letters") should ===(10)
        settings.LogDeadLetters should ===(10)

        getBoolean("pekko.log-dead-letters-during-shutdown") should ===(false)
        settings.LogDeadLettersDuringShutdown should ===(false)

        getDuration("pekko.log-dead-letters-suspend-duration", TimeUnit.MILLISECONDS) should ===(5 * 60 * 1000L)
        settings.LogDeadLettersSuspendDuration should ===(5.minutes)

        getBoolean("pekko.coordinated-shutdown.terminate-actor-system") should ===(true)
        settings.CoordinatedShutdownTerminateActorSystem should ===(true)

        getBoolean("pekko.coordinated-shutdown.run-by-actor-system-terminate") should ===(true)
        settings.CoordinatedShutdownRunByActorSystemTerminate should ===(true)

        getBoolean("pekko.actor.allow-java-serialization") should ===(false)
        settings.AllowJavaSerialization should ===(false)
      }

      {
        val c = config.getConfig("pekko.actor.default-dispatcher")

        // General dispatcher config

        {
          c.getString("type") should ===("Dispatcher")
          c.getString("executor") should ===("default-executor")
          c.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS) should ===(1 * 1000L)
          c.getInt("throughput") should ===(5)
          c.getDuration("throughput-deadline-time", TimeUnit.MILLISECONDS) should ===(0L)
          c.getBoolean("attempt-teamwork") should ===(true)
        }

        // Default executor config
        {
          val pool = c.getConfig("default-executor")
          pool.getString("fallback") should ===("fork-join-executor")
        }

        // Fork join executor config

        {
          val pool = c.getConfig("fork-join-executor")
          pool.getInt("parallelism-min") should ===(8)
          pool.getDouble("parallelism-factor") should ===(1.0)
          pool.getInt("parallelism-max") should ===(64)
          pool.getString("task-peeking-mode") should be("FIFO")
        }

        // Thread pool executor config

        {
          val pool = c.getConfig("thread-pool-executor")
          import pool._
          getDuration("keep-alive-time", TimeUnit.MILLISECONDS) should ===(60 * 1000L)
          getDouble("core-pool-size-factor") should ===(3.0)
          getDouble("max-pool-size-factor") should ===(3.0)
          getInt("task-queue-size") should ===(-1)
          getString("task-queue-type") should ===("linked")
          getBoolean("allow-core-timeout") should ===(true)
          getString("fixed-pool-size") should ===("off")
        }

        // Debug config
        {
          val debug = config.getConfig("pekko.actor.debug")
          import debug._
          getBoolean("receive") should ===(false)
          settings.AddLoggingReceive should ===(false)

          getBoolean("autoreceive") should ===(false)
          settings.DebugAutoReceive should ===(false)

          getBoolean("lifecycle") should ===(false)
          settings.DebugLifecycle should ===(false)

          getBoolean("fsm") should ===(false)
          settings.FsmDebugEvent should ===(false)

          getBoolean("event-stream") should ===(false)
          settings.DebugEventStream should ===(false)

          getBoolean("unhandled") should ===(false)
          settings.DebugUnhandledMessage should ===(false)

          getBoolean("router-misconfiguration") should ===(false)
          settings.DebugRouterMisconfiguration should ===(false)
        }

      }

      {
        val c = config.getConfig("pekko.actor.default-mailbox")

        // general mailbox config

        {
          c.getInt("mailbox-capacity") should ===(1000)
          c.getDuration("mailbox-push-timeout-time", TimeUnit.MILLISECONDS) should ===(10 * 1000L)
          c.getString("mailbox-type") should ===("org.apache.pekko.dispatch.UnboundedMailbox")
        }
      }
    }
  }

  "SLF4J Settings" must {
    "not be amended for default reference in pekko-actor" in {
      val dynamicAccess = system.asInstanceOf[ExtendedActorSystem].dynamicAccess
      val config = ActorSystem.Settings.amendSlf4jConfig(ConfigFactory.defaultReference(), dynamicAccess)
      config.getStringList("pekko.loggers").size() should ===(1)
      config.getStringList("pekko.loggers").get(0) should ===(classOf[DefaultLogger].getName)
      config.getString("pekko.logging-filter") should ===(classOf[DefaultLoggingFilter].getName)
    }

    "not be amended when pekko-slf4j is not in classpath" in {
      val dynamicAccess = system.asInstanceOf[ExtendedActorSystem].dynamicAccess
      val config = ActorSystem.Settings.amendSlf4jConfig(
        ConfigFactory.parseString("pekko.use-slf4j = on").withFallback(ConfigFactory.defaultReference()),
        dynamicAccess)
      config.getStringList("pekko.loggers").size() should ===(1)
      config.getStringList("pekko.loggers").get(0) should ===(classOf[DefaultLogger].getName)
      config.getString("pekko.logging-filter") should ===(classOf[DefaultLoggingFilter].getName)
    }
  }
}
