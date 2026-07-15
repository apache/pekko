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

package org.apache.pekko.stream.tck

import java.util.concurrent.TimeoutException

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.testng.annotations.AfterClass
import org.testng.annotations.BeforeClass

import pekko.actor.ActorSystem
import pekko.actor.ActorSystemImpl
import pekko.event.Logging
import pekko.testkit.EventFilter
import pekko.testkit.PekkoSpec
import pekko.testkit.TestEvent

trait ActorSystemLifecycle {

  protected var _system: ActorSystem = _

  implicit final def system: ActorSystem = _system

  def additionalConfig: Config = ConfigFactory.empty()

  def shutdownTimeout: FiniteDuration = Timeouts.actorSystemShutdownTimeoutMillis.millis

  // Always-applied baseline for TCK ActorSystems. The default Pekko
  // `actor-system-terminate` phase timeout is 10s, which is occasionally too
  // tight on JDK 25 nightly runs (virtualized dispatchers + heavy stochastic
  // TCK iterations leave stream actors mid-shutdown). Widening the phase
  // timeout gives the outer 40s shutdown await (see Timeouts) enough room to
  // drain leftover materializations cleanly instead of aborting the suite.
  private def baselineConfig: Config =
    ConfigFactory.parseString("pekko.coordinated-shutdown.phases.actor-system-terminate.timeout = 30 s")

  @BeforeClass
  def createActorSystem(): Unit = {
    val config = additionalConfig.withFallback(baselineConfig).withFallback(PekkoSpec.testConf)
    _system = ActorSystem(Logging.simpleName(getClass), config)
    _system.eventStream.publish(TestEvent.Mute(EventFilter[RuntimeException]("Test exception")))
  }

  @AfterClass
  def shutdownActorSystem(): Unit = {
    try {
      Await.ready(system.terminate(), shutdownTimeout)
    } catch {
      case _: TimeoutException =>
        val msg = "Failed to stop [%s] within [%s] \n%s".format(
          system.name,
          shutdownTimeout,
          system.asInstanceOf[ActorSystemImpl].printTree)
        throw new RuntimeException(msg)
    }
  }

}
