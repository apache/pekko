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

package org.apache.pekko.stream.io

import java.io.OutputStream

import scala.annotation.nowarn
import scala.util.Success

import org.apache.pekko
import pekko.Done
import pekko.stream.{ ActorMaterializer, ActorMaterializerSettings, IOOperationIncompleteException, Materializer }
import pekko.stream.scaladsl.{ Source, StreamConverters }
import pekko.stream.testkit._
import pekko.stream.testkit.Utils._
import pekko.testkit.TestProbe
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

@nowarn
class OutputStreamSinkSpec extends StreamSpec(UnboundedMailboxConfig) with ScalaFutures {

  val settings = ActorMaterializerSettings(system).withDispatcher("pekko.actor.default-dispatcher")
  implicit val materializer: Materializer = ActorMaterializer(settings)

  "OutputStreamSink" must {
    "write bytes to void OutputStream" in {
      val p = TestProbe()
      val data = List(ByteString("a"), ByteString("c"), ByteString("c"))

      val completion = Source(data).runWith(StreamConverters.fromOutputStream(() =>
        new OutputStream {
          override def write(i: Int): Unit = ()
          override def write(bytes: Array[Byte]): Unit = p.ref ! ByteString(bytes).utf8String
        }))

      p.expectMsg(data(0).utf8String)
      p.expectMsg(data(1).utf8String)
      p.expectMsg(data(2).utf8String)
      completion.futureValue.count shouldEqual 3
      completion.futureValue.status shouldEqual Success(Done)
    }

    "auto flush when enabled" in {
      val p = TestProbe()
      val data = List(ByteString("a"), ByteString("c"))
      Source(data).runWith(
        StreamConverters.fromOutputStream(
          () =>
            new OutputStream {
              override def write(i: Int): Unit = ()
              override def write(bytes: Array[Byte]): Unit = p.ref ! ByteString(bytes).utf8String
              override def flush(): Unit = p.ref ! "flush"
            },
          autoFlush = true))
      p.expectMsg(data(0).utf8String)
      p.expectMsg("flush")
      p.expectMsg(data(1).utf8String)
      p.expectMsg("flush")
    }

    "close underlying stream when error received" in {
      val p = TestProbe()
      Source
        .failed(TE("Boom!"))
        .runWith(StreamConverters.fromOutputStream(() =>
          new OutputStream {
            override def write(i: Int): Unit = ()
            override def close() = p.ref ! "closed"
          }))

      p.expectMsg("closed")
    }

    "complete materialized value with the error for upstream" in {
      val completion = Source
        .failed(TE("Boom!"))
        .runWith(StreamConverters.fromOutputStream(() =>
          new OutputStream {
            override def write(i: Int): Unit = ()
            override def close() = ()
          }))

      completion.failed.futureValue shouldBe an[IOOperationIncompleteException]
    }

    "complete materialized value with the error if creation fails" in {
      val completion = Source
        .single(ByteString(1))
        .runWith(StreamConverters.fromOutputStream(() => {
          throw TE("Boom!")
          new OutputStream {
            override def write(i: Int): Unit = ()
            override def close() = ()
          }
        }))

      completion.failed.futureValue shouldBe an[IOOperationIncompleteException]
    }

    "complete materialized value with the error if write fails" in {
      val completion = Source
        .single(ByteString(1))
        .runWith(StreamConverters.fromOutputStream(() => {
          new OutputStream {
            override def write(i: Int): Unit = {
              throw TE("Boom!")
            }
            override def close() = ()
          }
        }))

      completion.failed.futureValue shouldBe an[IOOperationIncompleteException]
    }

    "complete materialized value with the error if close fails" in {
      val completion = Source
        .single(ByteString(1))
        .runWith(StreamConverters.fromOutputStream(() =>
          new OutputStream {
            override def write(i: Int): Unit = ()
            override def close(): Unit = {
              throw TE("Boom!")
            }
          }))

      completion.failed.futureValue shouldBe an[IOOperationIncompleteException]
    }

    "close underlying stream when completion received" in {
      val p = TestProbe()
      Source.empty.runWith(StreamConverters.fromOutputStream(() =>
        new OutputStream {
          override def write(i: Int): Unit = ()
          override def write(bytes: Array[Byte]): Unit = p.ref ! ByteString(bytes).utf8String
          override def close() = p.ref ! "closed"
        }))

      p.expectMsg("closed")
    }

  }

}
