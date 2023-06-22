/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.cookbook

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{ Flow, Sink, Source }
import org.apache.pekko.stream.testkit._
import scala.concurrent.duration._
import org.apache.pekko.testkit.TestLatch
import scala.concurrent.Await

class RecipeSimpleDrop extends RecipeSpec {

  "Recipe for simply dropping elements for a faster stream" must {

    "work" in {

      // #simple-drop
      val droppyStream: Flow[Message, Message, NotUsed] =
        Flow[Message].conflate((lastMessage, newMessage) => newMessage)
      // #simple-drop
      val latch = TestLatch(2)
      val realDroppyStream =
        Flow[Message].conflate((lastMessage, newMessage) => { latch.countDown(); newMessage })

      val pub = TestPublisher.probe[Message]()
      val sub = TestSubscriber.manualProbe[Message]()
      val messageSource = Source.fromPublisher(pub)
      val sink = Sink.fromSubscriber(sub)

      messageSource.via(realDroppyStream).to(sink).run()

      val subscription = sub.expectSubscription()
      sub.expectNoMessage()

      pub.sendNext("1")
      pub.sendNext("2")
      pub.sendNext("3")

      Await.ready(latch, 1.second)

      subscription.request(1)
      sub.expectNext("3")

      pub.sendComplete()
      subscription.request(1)
      sub.expectComplete()
    }

  }

}
