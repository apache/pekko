/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import java.util.concurrent.{ Flow => JavaFlow }

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.{ Flow, JavaFlowSupport, Sink, Source }
import org.reactivestreams._

class IterablePublisherViaJavaFlowPublisherTest extends PekkoPublisherVerification[Int] {

  override def createPublisher(elements: Long): Publisher[Int] = {
    val sourceViaJavaFlowPublisher: JavaFlow.Publisher[Int] = Source(iterable(elements))
      .runWith(JavaFlowSupport.Sink.asPublisher(fanout = false))

    val javaFlowPublisherIntoPekkoSource: Source[Int, NotUsed] =
      JavaFlowSupport.Source.fromPublisher(sourceViaJavaFlowPublisher)

    javaFlowPublisherIntoPekkoSource
      .runWith(Sink.asPublisher(false)) // back as RS Publisher
  }

}
