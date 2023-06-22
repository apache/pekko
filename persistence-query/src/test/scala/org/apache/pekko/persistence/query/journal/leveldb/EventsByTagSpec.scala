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

import scala.concurrent.duration._
import org.apache.pekko
import pekko.persistence.journal.Tagged
import pekko.persistence.journal.WriteEventAdapter
import pekko.persistence.query.EventEnvelope
import pekko.persistence.query.NoOffset
import pekko.persistence.query.PersistenceQuery
import pekko.persistence.query.Sequence
import pekko.persistence.query.journal.leveldb.scaladsl.LeveldbReadJournal
import pekko.persistence.query.scaladsl.EventsByTagQuery
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.PekkoSpec
import pekko.testkit.ImplicitSender

import scala.annotation.nowarn

object EventsByTagSpec {
  val config = s"""
    pekko.loglevel = INFO
    pekko.persistence.journal.plugin = "pekko.persistence.journal.leveldb"

    pekko.persistence.journal.leveldb {
      dir = "target/journal-EventsByTagSpec"
      event-adapters {
        color-tagger  = org.apache.pekko.persistence.query.journal.leveldb.ColorTagger
      }
      event-adapter-bindings = {
        "java.lang.String" = color-tagger
      }
    }
    pekko.persistence.query.journal.leveldb {
      refresh-interval = 1s
      max-buffer-size = 2
    }
    pekko.test.single-expect-default = 10s
    
    leveldb-no-refresh = $${pekko.persistence.query.journal.leveldb}
    leveldb-no-refresh {
      refresh-interval = 10m
    }
    """

}

class ColorTagger extends WriteEventAdapter {
  val colors = Set("green", "black", "blue", "pink", "yellow")
  override def toJournal(event: Any): Any = event match {
    case s: String =>
      val tags = colors.foldLeft(Set.empty[String])((acc, c) => if (s.contains(c)) acc + c else acc)
      if (tags.isEmpty) event
      else Tagged(event, tags)
    case _ => event
  }

  override def manifest(event: Any): String = ""
}

class EventsByTagSpec extends PekkoSpec(EventsByTagSpec.config) with Cleanup with ImplicitSender {

  @nowarn("msg=deprecated")
  val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal](LeveldbReadJournal.Identifier)

  "Leveldb query EventsByTag" must {
    "implement standard EventsByTagQuery" in {
      queries.isInstanceOf[EventsByTagQuery] should ===(true)
    }

    "find existing events" in {
      val a = system.actorOf(TestActor.props("a"))
      val b = system.actorOf(TestActor.props("b"))
      a ! "hello"
      expectMsg(s"hello-done")
      a ! "a green apple"
      expectMsg(s"a green apple-done")
      b ! "a black car"
      expectMsg(s"a black car-done")
      a ! "a green banana"
      expectMsg(s"a green banana-done")
      b ! "a green leaf"
      expectMsg(s"a green leaf-done")

      val greenSrc = queries.currentEventsByTag(tag = "green", offset = NoOffset)
      greenSrc
        .runWith(TestSink.probe[Any])
        .request(2)
        .expectNext(EventEnvelope(Sequence(1L), "a", 2L, "a green apple", 0L))
        .expectNext(EventEnvelope(Sequence(2L), "a", 3L, "a green banana", 0L))
        .expectNoMessage(500.millis)
        .request(2)
        .expectNext(EventEnvelope(Sequence(3L), "b", 2L, "a green leaf", 0L))
        .expectComplete()

      val blackSrc = queries.currentEventsByTag(tag = "black", offset = Sequence(0L))
      blackSrc
        .runWith(TestSink.probe[Any])
        .request(5)
        .expectNext(EventEnvelope(Sequence(1L), "b", 1L, "a black car", 0L))
        .expectComplete()
    }

    "not see new events after demand request" in {
      val c = system.actorOf(TestActor.props("c"))

      val greenSrc = queries.currentEventsByTag(tag = "green", offset = Sequence(0L))
      val probe = greenSrc
        .runWith(TestSink.probe[Any])
        .request(2)
        .expectNext(EventEnvelope(Sequence(1L), "a", 2L, "a green apple", 0L))
        .expectNext(EventEnvelope(Sequence(2L), "a", 3L, "a green banana", 0L))
        .expectNoMessage(100.millis)

      c ! "a green cucumber"
      expectMsg(s"a green cucumber-done")

      probe
        .expectNoMessage(100.millis)
        .request(5)
        .expectNext(EventEnvelope(Sequence(3L), "b", 2L, "a green leaf", 0L))
        .expectComplete() // green cucumber not seen
    }

    "find events from offset (exclusive)" in {
      val greenSrc = queries.currentEventsByTag(tag = "green", offset = Sequence(2L))
      greenSrc
        .runWith(TestSink.probe[Any])
        .request(10)
        // note that banana is not included, since exclusive offset
        .expectNext(EventEnvelope(Sequence(3L), "b", 2L, "a green leaf", 0L))
        .expectNext(EventEnvelope(Sequence(4L), "c", 1L, "a green cucumber", 0L))
        .expectComplete()
    }

    "buffer elements until demand" in {
      val a = system.actorOf(TestActor.props("z"))
      a ! "a pink apple"
      expectMsg(s"a pink apple-done")
      a ! "a pink banana"
      expectMsg(s"a pink banana-done")
      a ! "a pink orange"
      expectMsg(s"a pink orange-done")

      val pinkSrc = queries.currentEventsByTag(tag = "pink")
      val probe = pinkSrc.runWith(TestSink.probe[Any])

      probe.request(1).expectNext(EventEnvelope(Sequence(1L), "z", 1L, "a pink apple", 0L))

      system.log.info("delay before demand")

      probe
        .expectNoMessage(200.millis)
        .request(3)
        .expectNext(EventEnvelope(Sequence(2L), "z", 2L, "a pink banana", 0L))
        .expectNext(EventEnvelope(Sequence(3L), "z", 3L, "a pink orange", 0L))
        .expectComplete()

    }

    "include timestamp in EventEnvelope" in {
      system.actorOf(TestActor.props("testTimestamp"))
      val greenSrc = queries.currentEventsByTag(tag = "green", offset = Sequence(0L))
      val probe = greenSrc.runWith(TestSink.probe[EventEnvelope])

      probe.request(2)
      probe.expectNext().timestamp should be > 0L
      probe.expectNext().timestamp should be > 0L
      probe.cancel()
    }
  }

  "Leveldb live query EventsByTag" must {
    "find new events" in {
      val d = system.actorOf(TestActor.props("d"))

      val blackSrc = queries.eventsByTag(tag = "black", offset = NoOffset)
      val probe = blackSrc.runWith(TestSink.probe[Any])

      try {

        probe.request(2).expectNext(EventEnvelope(Sequence(1L), "b", 1L, "a black car", 0L)).expectNoMessage(100.millis)

        d ! "a black dog"
        expectMsg(s"a black dog-done")
        d ! "a black night"
        expectMsg(s"a black night-done")

        probe
          .expectNext(EventEnvelope(Sequence(2L), "d", 1L, "a black dog", 0L))
          .expectNoMessage(100.millis)
          .request(10)
          .expectNext(EventEnvelope(Sequence(3L), "d", 2L, "a black night", 0L))
      } finally {
        probe.cancel()
      }
    }

    "find events from offset (exclusive)" in {
      val greenSrc = queries.eventsByTag(tag = "green", offset = Sequence(2L))
      val probe = greenSrc.runWith(TestSink.probe[Any])
      try {
        probe
          .request(10)
          // note that banana is not included, since exclusive offset
          .expectNext(EventEnvelope(Sequence(3L), "b", 2L, "a green leaf", 0L))
          .expectNext(EventEnvelope(Sequence(4L), "c", 1L, "a green cucumber", 0L))
          .expectNoMessage(100.millis)
      } finally {
        probe.cancel()
      }
    }

    "finds events without refresh" in {
      @nowarn("msg=deprecated")
      val queries = PersistenceQuery(system).readJournalFor[LeveldbReadJournal]("leveldb-no-refresh")
      val d = system.actorOf(TestActor.props("y"))

      d ! "a yellow car"
      expectMsg("a yellow car-done")

      val yellowSrc = queries.eventsByTag(tag = "yellow", offset = NoOffset)
      val probe = yellowSrc
        .runWith(TestSink.probe[Any])
        .request(2)
        .expectNext(EventEnvelope(Sequence(1L), "y", 1L, "a yellow car", 0L))
        .expectNoMessage(100.millis)

      d ! "a yellow dog"
      expectMsg(s"a yellow dog-done")
      d ! "a yellow night"
      expectMsg(s"a yellow night-done")

      probe
        .expectNext(EventEnvelope(Sequence(2L), "y", 2L, "a yellow dog", 0L))
        .expectNoMessage(100.millis)
        .request(10)
        .expectNext(EventEnvelope(Sequence(3L), "y", 3L, "a yellow night", 0L))

      probe.cancel()
    }

    "not complete for empty stream" in {
      val src = queries.eventsByTag(tag = "red", offset = NoOffset)
      val probe =
        src.map(_.event).runWith(TestSink.probe[Any]).request(2)

      probe.expectNoMessage(200.millis)

      probe.cancel()
    }
  }

}
