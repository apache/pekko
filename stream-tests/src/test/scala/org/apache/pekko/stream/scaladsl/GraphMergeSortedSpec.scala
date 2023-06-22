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
import org.scalacheck.Shrink
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks

import org.apache.pekko
import pekko.stream._
import pekko.stream.testkit.TwoStreamsSetup

@nowarn // tests deprecated apis
class GraphMergeSortedSpec extends TwoStreamsSetup with ScalaCheckPropertyChecks {

  override type Outputs = Int

  override def fixture(b: GraphDSL.Builder[_]): Fixture = new Fixture {
    val merge = b.add(new MergeSorted[Outputs])

    override def left: Inlet[Outputs] = merge.in0
    override def right: Inlet[Outputs] = merge.in1
    override def out: Outlet[Outputs] = merge.out
  }

  implicit def noShrink[T]: Shrink[T] =
    Shrink[T](_ => Stream.empty) // do not shrink failures, it only destroys evidence

  "MergeSorted" must {

    "work in the nominal case" in {
      val gen = Gen.listOf(Gen.oneOf(false, true))

      forAll(gen) { picks =>
        val N = picks.size
        val (left, right) = picks.zipWithIndex.partition(_._1)
        Source(left.map(_._2))
          .mergeSorted(Source(right.map(_._2)))
          .grouped(N max 1)
          .concat(Source.single(Nil))
          .runWith(Sink.head)
          .futureValue should ===(0 until N)
      }
    }

    commonTests()

  }
}
