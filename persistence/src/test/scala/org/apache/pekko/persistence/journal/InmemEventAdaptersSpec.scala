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

package org.apache.pekko.persistence.journal

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.testkit.PekkoSpec

import com.typesafe.config.ConfigFactory

@nowarn("msg=Unused import")
class InmemEventAdaptersSpec extends PekkoSpec {

  val config = ConfigFactory.parseString(s"""
      |pekko.persistence.journal {
      |  plugin = "pekko.persistence.journal.inmem"
      |
      |
      |  # adapters defined for all plugins
      |  common-event-adapter-bindings {
      |  }
      |
      |  inmem {
      |    # showcases re-using and concating configuration of adapters
      |
      |    event-adapters {
      |      example  = ${classOf[ExampleEventAdapter].getCanonicalName}
      |      marker   = ${classOf[MarkerInterfaceAdapter].getCanonicalName}
      |      precise  = ${classOf[PreciseAdapter].getCanonicalName}
      |      reader  = ${classOf[ReaderAdapter].getCanonicalName}
      |      writer  = ${classOf[WriterAdapter].getCanonicalName}
      |      another-reader = ${classOf[AnotherReaderAdapter].getCanonicalName}
      |    }
      |    event-adapter-bindings = {
      |      "${classOf[EventMarkerInterface].getCanonicalName}" = marker
      |      "java.lang.String" = example
      |      "org.apache.pekko.persistence.journal.PreciseAdapterEvent" = precise
      |      "org.apache.pekko.persistence.journal.ReadMeEvent" = reader
      |      "org.apache.pekko.persistence.journal.WriteMeEvent" = writer
      |      "org.apache.pekko.persistence.journal.ReadMeTwiceEvent" = [reader, another-reader]
      |    }
      |  }
      |}
    """.stripMargin).withFallback(ConfigFactory.load())

  val extendedActorSystem = system.asInstanceOf[ExtendedActorSystem]
  val inmemConfig = config.getConfig("pekko.persistence.journal.inmem")

  "EventAdapters" must {
    "parse configuration and resolve adapter definitions" in {
      val adapters = EventAdapters(extendedActorSystem, inmemConfig)
      adapters.get(classOf[EventMarkerInterface]).getClass should ===(classOf[MarkerInterfaceAdapter])
    }

    "pick the most specific adapter available" in {
      val adapters = EventAdapters(extendedActorSystem, inmemConfig)

      // precise case, matching non-user classes
      adapters.get(classOf[java.lang.String]).getClass should ===(classOf[ExampleEventAdapter])

      // pick adapter by implemented marker interface
      adapters.get(classOf[SampleEvent]).getClass should ===(classOf[MarkerInterfaceAdapter])

      // more general adapter matches as well, but most specific one should be picked
      adapters.get(classOf[PreciseAdapterEvent]).getClass should ===(classOf[PreciseAdapter])

      // no adapter defined for Long, should return identity adapter
      import org.scalatest.matchers.should.Matchers.unconstrainedEquality
      adapters.get(classOf[java.lang.Long]).getClass should ===(IdentityEventAdapter.getClass)
    }

    "fail with useful message when binding to not defined adapter" in {
      val badConfig = ConfigFactory.parseString("""
          |pekko.persistence.journal.inmem {
          |  event-adapter-bindings {
          |    "java.lang.Integer" = undefined-adapter
          |  }
          |}
        """.stripMargin)

      val combinedConfig = badConfig.getConfig("pekko.persistence.journal.inmem")
      val ex = intercept[IllegalArgumentException] {
        EventAdapters(extendedActorSystem, combinedConfig)
      }

      ex.getMessage should include("java.lang.Integer was bound to undefined event-adapter: undefined-adapter")
    }

    "allow implementing only the read-side (ReadEventAdapter)" in {
      val adapters = EventAdapters(extendedActorSystem, inmemConfig)

      // read-side only adapter
      val r: EventAdapter = adapters.get(classOf[ReadMeEvent])
      r.fromJournal(r.toJournal(ReadMeEvent()), "").events.head.toString should ===("from-ReadMeEvent()")
    }

    "allow implementing only the write-side (WriteEventAdapter)" in {
      val adapters = EventAdapters(extendedActorSystem, inmemConfig)

      // write-side only adapter
      val w: EventAdapter = adapters.get(classOf[WriteMeEvent])
      w.fromJournal(w.toJournal(WriteMeEvent()), "").events.head.toString should ===("to-WriteMeEvent()")
    }

    "allow combining only the read-side (CombinedReadEventAdapter)" in {
      val adapters = EventAdapters(extendedActorSystem, inmemConfig)

      // combined-read-side only adapter
      val r: EventAdapter = adapters.get(classOf[ReadMeTwiceEvent])
      r.fromJournal(r.toJournal(ReadMeTwiceEvent()), "").events.map(_.toString) shouldBe Seq(
        "from-ReadMeTwiceEvent()",
        "again-ReadMeTwiceEvent()")
    }
  }

}

abstract class BaseTestAdapter extends EventAdapter {
  override def toJournal(event: Any): Any = event
  override def fromJournal(event: Any, manifest: String): EventSeq = EventSeq.single(event)
  override def manifest(event: Any): String = ""
}

class ExampleEventAdapter extends BaseTestAdapter {}
class MarkerInterfaceAdapter extends BaseTestAdapter {}
class PreciseAdapter extends BaseTestAdapter {}

case class ReadMeEvent()
case class ReadMeTwiceEvent()
class ReaderAdapter extends ReadEventAdapter {
  override def fromJournal(event: Any, manifest: String): EventSeq =
    EventSeq("from-" + event)
}
class AnotherReaderAdapter extends ReadEventAdapter {
  override def fromJournal(event: Any, manifest: String): EventSeq =
    EventSeq("again-" + event)
}

case class WriteMeEvent()
class WriterAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""
  override def toJournal(event: Any): Any = "to-" + event
}

trait EventMarkerInterface
final case class SampleEvent() extends EventMarkerInterface
final case class PreciseAdapterEvent() extends EventMarkerInterface
