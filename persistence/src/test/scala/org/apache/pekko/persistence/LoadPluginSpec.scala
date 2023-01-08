/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.Actor
import pekko.persistence.journal.inmem.InmemJournal
import pekko.testkit.ImplicitSender
import pekko.util.unused

object LoadPluginSpec {

  case object GetConfig

  class JournalWithConfig(val config: Config) extends InmemJournal {
    override def receivePluginInternal: Actor.Receive = {
      case GetConfig => sender() ! config
    }
  }

  object JournalWithStartupNotification {
    final case class Started(configPath: String)
  }
  class JournalWithStartupNotification(@unused config: Config, configPath: String) extends InmemJournal {
    context.system.eventStream.publish(JournalWithStartupNotification.Started(configPath))
  }
}

class LoadPluginSpec
    extends PersistenceSpec(
      PersistenceSpec.config(
        "inmem",
        "LoadJournalSpec",
        extraConfig = Some("""
  pekko.persistence.journal.inmem.class = "org.apache.pekko.persistence.LoadPluginSpec$JournalWithConfig"
  pekko.persistence.journal.inmem.extra-property = 17
  
  test-plugin {
    class = "org.apache.pekko.persistence.LoadPluginSpec$JournalWithStartupNotification"
  }
  """)))
    with ImplicitSender {
  import LoadPluginSpec._

  "A journal" must {
    "be created with plugin config" in {
      val journalRef = Persistence(system).journalFor("pekko.persistence.journal.inmem")
      journalRef ! GetConfig
      expectMsgType[Config].getInt("extra-property") should be(17)
    }

    "not be created via eventAdapter" in {
      system.eventStream.subscribe(testActor, classOf[JournalWithStartupNotification.Started])
      Persistence(system).adaptersFor("test-plugin")
      expectNoMessage()
      Persistence(system).journalFor("test-plugin")
      expectMsg(JournalWithStartupNotification.Started("test-plugin"))
    }
  }
}
