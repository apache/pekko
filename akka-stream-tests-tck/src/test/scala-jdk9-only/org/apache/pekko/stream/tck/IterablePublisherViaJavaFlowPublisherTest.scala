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

    val javaFlowPublisherIntoAkkaSource: Source[Int, NotUsed] =
      JavaFlowSupport.Source.fromPublisher(sourceViaJavaFlowPublisher)

    javaFlowPublisherIntoAkkaSource
      .runWith(Sink.asPublisher(false)) // back as RS Publisher
  }

}
