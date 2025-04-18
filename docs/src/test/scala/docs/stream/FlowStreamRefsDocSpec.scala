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

package docs.stream

import docs.CompileOnlySpec
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.{ Actor, Props }
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.testkit.PekkoSpec

object FlowStreamRefsDocSpec {
  // #offer-source
  case class RequestLogs(streamId: Int)
  // #offer-source

  // #offer-sink
  case class PrepareUpload(id: String)
  // #offer-sink

}

class FlowStreamRefsDocSpec extends PekkoSpec with CompileOnlySpec {

  "offer a source ref" in compileOnlySpec {
    // #offer-source
    import FlowStreamRefsDocSpec._
    import org.apache.pekko
    import pekko.stream.SourceRef

    case class LogsOffer(streamId: Int, sourceRef: SourceRef[String])

    class DataSource extends Actor {

      def receive: Receive = {
        case RequestLogs(streamId) =>
          // obtain the source you want to offer:
          val source: Source[String, NotUsed] = streamLogs(streamId)

          // materialize the SourceRef:
          val ref: SourceRef[String] = source.runWith(StreamRefs.sourceRef())

          // wrap the SourceRef in some domain message, such that the sender knows what source it is
          val reply = LogsOffer(streamId, ref)

          // reply to sender
          sender() ! reply
      }

      def streamLogs(streamId: Long): Source[String, NotUsed] = ???
    }
    // #offer-source

    // #offer-source-use
    val sourceActor = system.actorOf(Props[DataSource](), "dataSource")

    sourceActor ! RequestLogs(1337)
    val offer = expectMsgType[LogsOffer]

    // implicitly converted to a Source:
    offer.sourceRef.runWith(Sink.foreach(println))
    // alternatively explicitly obtain Source from SourceRef:
    // offer.sourceRef.source.runWith(Sink.foreach(println))

    // #offer-source-use
  }

  "offer a sink ref" in compileOnlySpec {
    // #offer-sink
    import FlowStreamRefsDocSpec._
    import org.apache.pekko.stream.SinkRef

    case class MeasurementsSinkReady(id: String, sinkRef: SinkRef[String])

    class DataReceiver extends Actor {

      def receive: Receive = {
        case PrepareUpload(nodeId) =>
          // obtain the source you want to offer:
          val sink: Sink[String, NotUsed] = logsSinkFor(nodeId)

          // materialize the SinkRef (the remote is like a source of data for us):
          val ref: SinkRef[String] = StreamRefs.sinkRef[String]().to(sink).run()

          // wrap the SinkRef in some domain message, such that the sender knows what source it is
          val reply = MeasurementsSinkReady(nodeId, ref)

          // reply to sender
          sender() ! reply
      }

      def logsSinkFor(nodeId: String): Sink[String, NotUsed] = ???
    }

    // #offer-sink

    def localMetrics(): Source[String, NotUsed] = Source.single("")

    // #offer-sink-use
    val receiver = system.actorOf(Props[DataReceiver](), "receiver")

    receiver ! PrepareUpload("system-42-tmp")
    val ready = expectMsgType[MeasurementsSinkReady]

    // stream local metrics to Sink's origin:
    localMetrics().runWith(ready.sinkRef)
    // #offer-sink-use
  }

  "show how to configure timeouts with attrs" in compileOnlySpec {
    // #attr-sub-timeout
    // configure the timeout for source
    import org.apache.pekko.stream.StreamRefAttributes

    import scala.concurrent.duration._

    // configuring Sink.sourceRef (notice that we apply the attributes to the Sink!):
    Source
      .repeat("hello")
      .runWith(StreamRefs.sourceRef().addAttributes(StreamRefAttributes.subscriptionTimeout(5.seconds)))

    // configuring SinkRef.source:
    StreamRefs
      .sinkRef()
      .addAttributes(StreamRefAttributes.subscriptionTimeout(5.seconds))
      .runWith(Sink.ignore) // not very interesting Sink, just an example
    // #attr-sub-timeout
  }

}
