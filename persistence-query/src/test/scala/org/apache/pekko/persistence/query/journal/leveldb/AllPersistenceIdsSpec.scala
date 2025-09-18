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

package org.apache.pekko.persistence.query.journal.leveldb

import scala.annotation.nowarn
import scala.concurrent.duration._

import org.apache.pekko
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import pekko.persistence.query.scaladsl.PersistenceIdsQuery
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec

object AllPersistenceIdsSpec {
  val config = """
    pekko.loglevel = INFO
    pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb"
    pekko.persistence.journal.leveldb.dir = "target/journal-AllPersistenceIdsSpec"
    pekko.test.single-expect-default = 10s
    # test is using Java serialization and not priority to rewrite
    pekko.actor.allow-java-serialization = on
    pekko.actor.warn-about-java-serializer-usage = off
    """
}

class AllPersistenceIdsSpec extends PekkoSpec(AllPersistenceIdsSpec.config) with Cleanup with ImplicitSender {

  @nowarn("msg=deprecated")
  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

  "Leveldb query AllPersistenceIds" must {

    "implement standard AllPersistenceIdsQuery" in {
      queries.isInstanceOf[PersistenceIdsQuery] should ===(true)
    }

    "find existing persistenceIds" in {
      system.actorOf(TestActor.props("a")) ! "a1"
      expectMsg("a1-done")
      system.actorOf(TestActor.props("b")) ! "b1"
      expectMsg("b1-done")
      system.actorOf(TestActor.props("c")) ! "c1"
      expectMsg("c1-done")

      val src = queries.currentPersistenceIds()
      val probe = src.runWith(TestSink.probe[String])
      probe.within(10.seconds) {
        probe.request(5).expectNextUnordered("a", "b", "c").expectComplete()
      }
    }

    "find new persistenceIds" in {
      // a, b, c created by previous step
      system.actorOf(TestActor.props("d")) ! "d1"
      expectMsg("d1-done")

      val src = queries.persistenceIds()
      val probe = src.runWith(TestSink.probe[String])
      probe.within(10.seconds) {
        probe.request(5).expectNextUnorderedN(List("a", "b", "c", "d"))

        system.actorOf(TestActor.props("e")) ! "e1"
        probe.expectNext("e")

        val more = (1 to 100).map("f" + _)
        more.foreach { p =>
          system.actorOf(TestActor.props(p)) ! p
        }

        probe.request(100)
        probe.expectNextUnorderedN(more)
      }

    }
  }

}
