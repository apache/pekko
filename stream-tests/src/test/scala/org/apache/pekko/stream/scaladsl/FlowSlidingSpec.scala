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

import scala.annotation.nowarn

import org.scalacheck.Gen
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import org.apache.pekko
import pekko.pattern.pipe
import pekko.stream.{ ActorMaterializer, ActorMaterializerSettings, Materializer }
import pekko.stream.testkit._

@nowarn
class FlowSlidingSpec extends StreamSpec with ScalaCheckPropertyChecks {
  import system.dispatcher
  val settings = ActorMaterializerSettings(system).withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer: Materializer = ActorMaterializer(settings)

  "Sliding" must {
    import org.scalacheck.Shrink.shrinkAny
    def check(gen: Gen[(Int, Int, Int)]): Unit =
      forAll(gen, minSize(1000), sizeRange(0)) {
        case (len, win, step) =>
          val af = Source
            .fromIterator(() => Iterator.from(0).take(len))
            .sliding(win, step)
            .runFold(Seq.empty[Seq[Int]])(_ :+ _)
          val cf = Source
            .fromIterator(() => Iterator.from(0).take(len).sliding(win, step))
            .runFold(Seq.empty[Seq[Int]])(_ :+ _)
          af.futureValue should be(cf.futureValue)
      }

    "behave just like collections sliding with step < window" in {
      check(for {
        len <- Gen.choose(0, 31)
        win <- Gen.choose(1, 61)
        step <- Gen.choose(1, (win - 1) max 1)
      } yield (len, win, step))
    }

    "behave just like collections sliding with step == window" in {
      check(for {
        len <- Gen.choose(0, 31)
        win <- Gen.choose(1, 61)
        step <- Gen.const(win)
      } yield (len, win, step))
    }

    "behave just like collections sliding with step > window" in {
      check(for {
        len <- Gen.choose(0, 31)
        win <- Gen.choose(1, 61)
        step <- Gen.choose(win + 1, 127)
      } yield (len, win, step))
    }

    "work with empty sources" in {
      Source.empty.sliding(1).runForeach(testActor ! _).map(_ => "done").pipeTo(testActor)
      expectMsg("done")
    }
  }
}
