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

package org.apache.pekko.persistence

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.{ Actor, Props }
import pekko.event.Logging
import pekko.event.Logging.Warning
import pekko.testkit.{ EventFilter, ImplicitSender, TestEvent }

object OptionalSnapshotStoreSpec {

  class AnyPersistentActor(name: String) extends PersistentActor {
    var lastSender = context.system.deadLetters

    override def persistenceId = name
    override def receiveCommand: Receive = {
      case s: String =>
        lastSender = sender()
        saveSnapshot(s)
      case f: SaveSnapshotFailure => lastSender ! f
      case s: SaveSnapshotSuccess => lastSender ! s
    }
    override def receiveRecover: Receive = Actor.emptyBehavior
  }

  class PickedSnapshotStorePersistentActor(name: String) extends AnyPersistentActor(name) {
    override def snapshotPluginId: String = "pekko.persistence.snapshot-store.local"
  }
}

class OptionalSnapshotStoreSpec extends PersistenceSpec(ConfigFactory.parseString(s"""
    pekko.persistence.publish-plugin-commands = on
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"

    pekko.actor.warn-about-java-serializer-usage = off

    # snapshot store plugin is NOT defined, things should still work
    pekko.persistence.snapshot-store.local.dir = "target/snapshots-${classOf[OptionalSnapshotStoreSpec].getName}/"
  """)) with ImplicitSender {
  import OptionalSnapshotStoreSpec._

  system.eventStream.publish(TestEvent.Mute(EventFilter[pekko.pattern.AskTimeoutException]()))

  "Persistence extension" must {
    "initialize properly even in absence of configured snapshot store" in {
      system.eventStream.subscribe(testActor, classOf[Logging.Warning])
      system.actorOf(Props(classOf[AnyPersistentActor], name))
      val message = expectMsgType[Warning].message.toString
      message should include("No default snapshot store configured")
    }

    "fail if PersistentActor tries to saveSnapshot without snapshot-store available" in {
      val persistentActor = system.actorOf(Props(classOf[AnyPersistentActor], name))
      persistentActor ! "snap"
      expectMsgType[SaveSnapshotFailure].cause.getMessage should include("No snapshot store configured")
    }

    "successfully save a snapshot when no default snapshot-store configured, yet PersistentActor picked one explicitly" in {
      val persistentActor = system.actorOf(Props(classOf[PickedSnapshotStorePersistentActor], name))
      persistentActor ! "snap"
      expectMsgType[SaveSnapshotSuccess]
    }
  }
}
