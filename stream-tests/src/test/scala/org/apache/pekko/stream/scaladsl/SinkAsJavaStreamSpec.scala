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

package org.apache.pekko.stream.scaladsl

import java.util.stream.Collectors

import org.apache.pekko
import pekko.stream._
import pekko.stream.impl.PhasedFusingActorMaterializer
import pekko.stream.impl.StreamSupervisor
import pekko.stream.impl.StreamSupervisor.Children
import pekko.stream.testkit._
import pekko.stream.testkit.Utils._
import pekko.stream.testkit.scaladsl.TestSource
import pekko.util.ByteString

class SinkAsJavaStreamSpec extends StreamSpec(UnboundedMailboxConfig) {

  "Java Stream Sink" must {

    "work in happy case" in {
      val javaSource = Source(1 to 100).runWith(StreamConverters.asJavaStream())
      javaSource.count() should ===(100L)
      //
      Source(1 to 100).runWith(Sink.asJavaStream())
        .count() should ===(100L)
    }

    "fail if parent stream is failed" in {
      val javaSource = Source(1 to 100).map(_ => throw TE("")).runWith(StreamConverters.asJavaStream())
      a[TE] shouldBe thrownBy {
        javaSource.findFirst()
      }
    }

    "work with collector that is assigned to materialized value" in {
      val javaSource = Source(1 to 100).map(_.toString).runWith(StreamConverters.asJavaStream())
      javaSource.collect(Collectors.joining(", ")) should ===((1 to 100).mkString(", "))
    }

    "work with empty stream" in {
      val javaSource = Source.empty.runWith(StreamConverters.asJavaStream())
      javaSource.count() should ===(0L)
    }

    "work with endless stream" in {
      val javaSource = Source.repeat(1).runWith(StreamConverters.asJavaStream())
      javaSource.limit(10).count() should ===(10L)
      javaSource.close()
    }

    "allow overriding the dispatcher using Attributes" in {
      val probe = TestSource
        .probe[ByteString]
        .to(StreamConverters.asJavaStream().addAttributes(ActorAttributes.dispatcher("pekko.actor.default-dispatcher")))
        .run()
      SystemMaterializer(system).materializer
        .asInstanceOf[PhasedFusingActorMaterializer]
        .supervisor
        .tell(StreamSupervisor.GetChildren, testActor)
      val ref = expectMsgType[Children].children.find(_.path.toString contains "asJavaStream").get
      assertDispatcher(ref, "pekko.actor.default-dispatcher")
      probe.sendComplete()
    }

    "work in separate IO dispatcher" in {
      val materializer = Materializer.createMaterializer(system)
      TestSource.probe[ByteString].runWith(StreamConverters.asJavaStream())(materializer)
      materializer.asInstanceOf[PhasedFusingActorMaterializer].supervisor.tell(StreamSupervisor.GetChildren, testActor)
      val ref = expectMsgType[Children].children.find(_.path.toString contains "asJavaStream").get
      assertDispatcher(ref, ActorAttributes.IODispatcher.dispatcher)
    }
  }
}
