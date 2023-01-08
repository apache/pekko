/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.FlowMonitor
import org.apache.pekko.stream.FlowMonitorState
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

class Monitor {

  implicit val system: ActorSystem = ActorSystem("monitor-sample-sys2")
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  // #monitor
  val source: Source[Int, NotUsed] =
    Source.fromIterator(() => Iterator.from(0))

  def printMonitorState(flowMonitor: FlowMonitor[Int]) =
    flowMonitor.state match {
      case FlowMonitorState.Initialized =>
        println("Stream is initialized but hasn't processed any element")
      case FlowMonitorState.Received(msg) =>
        println(s"Last element processed: $msg")
      case FlowMonitorState.Failed(cause) =>
        println(s"Stream failed with cause $cause")
      case FlowMonitorState.Finished => println(s"Stream completed already")
    }

  val monitoredSource: Source[Int, FlowMonitor[Int]] = source.take(6).throttle(5, 1.second).monitorMat(Keep.right)
  val (flowMonitor, futureDone) =
    monitoredSource.toMat(Sink.foreach(println))(Keep.both).run()

  // If we peek in the monitor too early, it's possible it was not initialized yet.
  printMonitorState(flowMonitor)

  // Periodically check the monitor
  Source.tick(200.millis, 400.millis, "").runForeach(_ => printMonitorState(flowMonitor))

  // #monitor
  futureDone.onComplete(_ => system.terminate())

}
