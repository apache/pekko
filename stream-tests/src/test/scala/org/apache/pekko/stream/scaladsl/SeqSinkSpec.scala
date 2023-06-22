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

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.Future

import org.apache.pekko
import pekko.stream.AbruptTerminationException
import pekko.stream.Materializer
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.TestPublisher

class SeqSinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) {

  "Sink.toSeq" must {
    "return a Seq[T] from a Source" in {
      val input = 1 to 6
      val future: Future[immutable.Seq[Int]] = Source(input).runWith(Sink.seq)
      val result: immutable.Seq[Int] = Await.result(future, remainingOrDefault)
      result should be(input.toSeq)
    }

    "return an empty Seq[T] from an empty Source" in {
      val input: immutable.Seq[Int] = Nil
      val future: Future[immutable.Seq[Int]] = Source.fromIterator(() => input.iterator).runWith(Sink.seq)
      val result: immutable.Seq[Int] = Await.result(future, remainingOrDefault)
      result should be(input)
    }

    "fail the future on abrupt termination" in {
      val mat = Materializer(system)
      val probe = TestPublisher.probe()
      val future: Future[immutable.Seq[Int]] =
        Source.fromPublisher(probe).runWith(Sink.seq)(mat)
      mat.shutdown()
      future.failed.futureValue shouldBe an[AbruptTerminationException]
    }
  }
}
