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

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ ActorSystem, Address }
import pekko.testkit._
import pekko.util.ccompat._
import pekko.util.ccompat.JavaConverters._

@ccompatUsedUntil213
class DaemonicSpec extends PekkoSpec {

  "Remoting configured with daemonic = on" must {

    "shut down correctly after getting connection refused" in {
      // get all threads running before actor system is started
      val origThreads: Set[Thread] = Thread.getAllStackTraces.keySet().asScala.to(Set)
      // create a separate actor system that we can check the threads for
      val daemonicSystem = ActorSystem(
        "daemonic",
        ConfigFactory.parseString("""
        pekko.daemonic = on
        pekko.actor.provider = remote
        pekko.remote.classic.netty.tcp.port = 0
        pekko.remote.artery.canonical.port = 0
        pekko.log-dead-letters-during-shutdown = off
        #pekko.remote.artery.advanced.aeron.idle-cpu = 5
      """))

      try {
        val unusedPort = 86 // very unlikely to ever be used, "system port" range reserved for Micro Focus Cobol

        val protocol = if (RARP(daemonicSystem).provider.remoteSettings.Artery.Enabled) "pekko" else "pekko.tcp"
        val unusedAddress =
          RARP(daemonicSystem).provider.getExternalAddressFor(Address(protocol, "", "", unusedPort)).get
        val selection = daemonicSystem.actorSelection(s"$unusedAddress/user/SomeActor")
        selection ! "whatever"

        // get new non daemonic threads running
        awaitAssert({
            val newNonDaemons: Set[Thread] =
              Thread.getAllStackTraces.keySet().asScala.filter(t => !origThreads(t) && !t.isDaemon).to(Set)
            newNonDaemons should ===(Set.empty[Thread])
          }, 4.seconds)

      } finally
        shutdown(daemonicSystem)
    }
  }
}
