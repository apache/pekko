/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.util.concurrent.ThreadLocalRandom.{ current => random }

import scala.collection.immutable

import org.apache.pekko
import pekko.stream.testkit.ScriptedTest
import pekko.stream.testkit.StreamSpec

class FlowGroupedSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 2
  """) with ScriptedTest {

  "A Grouped" must {

    def randomSeq(n: Int) = immutable.Seq.fill(n)(random.nextInt())
    def randomTest(n: Int) = { val s = randomSeq(n); s -> immutable.Seq(s) }

    "group evenly" in {
      val testLen = random.nextInt(1, 16)
      def script =
        Script(TestConfig.RandomTestRange.map { _ =>
          randomTest(testLen)
        }: _*)
      TestConfig.RandomTestRange.foreach(_ => runScript(script)(_.grouped(testLen)))
    }

    "group with rest" in {
      val testLen = random.nextInt(1, 16)
      def script =
        Script(TestConfig.RandomTestRange.map { _ =>
          randomTest(testLen)
        } :+ randomTest(1): _*)
      TestConfig.RandomTestRange.foreach(_ => runScript(script)(_.grouped(testLen)))
    }

  }

}
