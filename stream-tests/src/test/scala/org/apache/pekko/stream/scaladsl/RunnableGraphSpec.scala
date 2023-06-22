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

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.Attributes
import pekko.stream.javadsl
import pekko.stream.testkit.StreamSpec

class RunnableGraphSpec extends StreamSpec {

  "A RunnableGraph" must {

    "suitably override attribute handling methods" in {
      import Attributes._
      val r: RunnableGraph[NotUsed] =
        RunnableGraph.fromGraph(Source.empty.to(Sink.ignore)).async.addAttributes(none).named("useless")

      val name = r.traversalBuilder.attributes.get[Name]
      name shouldEqual Some(Name("useless"))
      val boundary = r.traversalBuilder.attributes.get[AsyncBoundary.type]
      boundary shouldEqual (Some(AsyncBoundary))
    }

    "allow conversion from scala to java" in {
      val runnable: javadsl.RunnableGraph[NotUsed] = Source.empty.to(Sink.ignore).asJava
      runnable.run(system) shouldBe NotUsed
    }

    "allow conversion from java to scala" in {
      val runnable: RunnableGraph[NotUsed] = javadsl.Source.empty().to(javadsl.Sink.ignore()).asScala
      runnable.run() shouldBe NotUsed
    }

  }
}
