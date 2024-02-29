/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.Attributes
import pekko.stream.testkit.TestPublisher
import pekko.testkit.TestKit

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike

class SourceFromPublisherSpec
    extends TestKit(ActorSystem("source-from-publisher-spec"))
    with AsyncWordSpecLike
    with Matchers {

  "Source.fromPublisher" should {
    // https://github.com/akka/akka/pull/31129
    "consider 'inputBuffer' attributes in a correct way" in pendingUntilFixed {
      val publisher = TestPublisher.probe[Int]()
      Source.fromPublisher(publisher).addAttributes(Attributes.inputBuffer(1, 2)).runWith(Sink.ignore)
      publisher.expectRequest() should ===(2L)
    }
  }
}
