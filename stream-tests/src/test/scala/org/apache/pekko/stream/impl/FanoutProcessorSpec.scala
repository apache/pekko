/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils.TE
import pekko.testkit.TestProbe

class FanoutProcessorSpec extends StreamSpec {

  "The FanoutProcessor" must {

    // #25634
    "not leak running actors on failed upstream without subscription" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      probe.watch(publisherRef)
      promise.failure(TE("boom"))
      probe.expectTerminated(publisherRef)
    }

    // #25634
    "not leak running actors on failed upstream with one subscription" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)
      val boom = TE("boom")
      promise.failure(boom)
      probe.expectTerminated(publisherRef)
    }

    // #25634
    "not leak running actors on failed upstream with multiple subscriptions" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      probe.watch(publisherRef)
      Source.fromPublisher(publisher).runWith(Sink.ignore)
      Source.fromPublisher(publisher).runWith(Sink.ignore)
      val boom = TE("boom")
      promise.failure(boom)
      probe.expectTerminated(publisherRef)
    }

    "not leak running actors on completed upstream no subscriptions" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      probe.watch(publisherRef)
      promise.success(None)

      probe.expectTerminated(publisherRef)
    }

    "not leak running actors on completed upstream with one subscription" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      val completed = Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)

      promise.success(None)

      probe.expectTerminated(publisherRef)
      // would throw if not completed
      completed.futureValue
    }

    "not leak running actors on completed upstream with multiple subscriptions" in {
      val probe = TestProbe()
      val (promise, publisher) = Source.maybe[Int].toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      val completed1 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      val completed2 = Source.fromPublisher(publisher).runWith(Sink.ignore)
      probe.watch(publisherRef)
      promise.success(None)

      probe.expectTerminated(publisherRef)
      // would throw if not completed
      completed1.futureValue
      completed2.futureValue
    }

    "not leak running actors on failed downstream" in {
      val probe = TestProbe()
      val (_, publisher) = Source.repeat(1).toMat(Sink.asPublisher(true))(Keep.both).run()
      val publisherRef = publisher.asInstanceOf[ActorPublisher[Int]].impl
      probe.watch(publisherRef)
      Source.fromPublisher(publisher).map(_ => throw TE("boom")).runWith(Sink.ignore)
      probe.expectTerminated(publisherRef)
    }

  }

}
