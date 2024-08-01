/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.annotation.nowarn

import org.apache.pekko
import pekko.stream.ActorMaterializer
import pekko.stream.ActorMaterializerSettings
import pekko.stream.testkit.StreamSpec
import pekko.testkit.TestProbe

@nowarn("msg=deprecated")
class FlowDispatcherSpec extends StreamSpec(s"my-dispatcher = $${pekko.test.stream-dispatcher}") {

  val defaultSettings = ActorMaterializerSettings(system)

  def testDispatcher(
      settings: ActorMaterializerSettings = defaultSettings,
      dispatcher: String = "pekko.test.stream-dispatcher") = {

    implicit val materializer: ActorMaterializer = ActorMaterializer(settings)

    val probe = TestProbe()
    Source(List(1, 2, 3)).map { i => probe.ref ! Thread.currentThread().getName(); i }.to(Sink.ignore).run()
    probe.receiveN(3).foreach {
      case s: String  => s should startWith(system.name + "-" + dispatcher)
      case unexpected => throw new RuntimeException(s"Unexpected: $unexpected")
    }
  }

  "Flow with dispatcher setting" must {
    "use the default dispatcher" in testDispatcher()

    "use custom dispatcher" in testDispatcher(defaultSettings.withDispatcher("my-dispatcher"), "my-dispatcher")
  }
}
