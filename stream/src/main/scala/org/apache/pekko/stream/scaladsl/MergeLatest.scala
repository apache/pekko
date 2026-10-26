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

package org.apache.pekko.stream.scaladsl

import scala.collection.immutable

import org.apache.pekko
import pekko.stream.{ Attributes, Inlet, Outlet, UniformFanInShape }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

/**
 * MergeLatest joins elements from N input streams into stream of lists of size N.
 * i-th element in list is the latest emitted element from i-th input stream.
 * MergeLatest emits the latest values from all input streams after each stream has emitted at least one element.
 * By default, updates are conflated while downstream is back-pressured. Set
 * `pekko.stream.materializer.merge-latest.mode = legacy` to retain the previous behavior of buffering every update.
 *
 * '''Emits when''' an input element is available and each input has emitted at least one element from stream start;
 * in the default mode, at most the latest combination is retained while downstream is back-pressured
 *
 * '''Completes when''' all upstreams complete (eagerClose=false) or one upstream completes (eagerClose=true)
 *
 * '''Cancels when''' downstream cancels
 */
object MergeLatest {

  /**
   * Create a new `MergeLatest` with the specified number of input ports.
   *
   * @param inputPorts number of input ports
   * @param eagerComplete if true, the merge latest will complete as soon as one of its inputs completes.
   */
  def apply[T](inputPorts: Int, eagerComplete: Boolean = false): GraphStage[UniformFanInShape[T, List[T]]] =
    new MergeLatest[T, List[T]](inputPorts, eagerComplete)(_.toList)

}

final class MergeLatest[T, M](val inputPorts: Int, val eagerClose: Boolean)(buildElem: Array[T] => M)
    extends GraphStage[UniformFanInShape[T, M]] {
  require(inputPorts >= 1, "input ports must be >= 1")

  val in: immutable.IndexedSeq[Inlet[T]] = Vector.tabulate(inputPorts)(i => Inlet[T]("MergeLatest.in" + i))
  val out: Outlet[M] = Outlet[M]("MergeLatest.out")
  override val shape: UniformFanInShape[T, M] = UniformFanInShape(out, in: _*)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with OutHandler {
      private val activeStreams: java.util.HashSet[Int] = new java.util.HashSet[Int]()
      private var runningUpstreams: Int = inputPorts
      private def upstreamsClosed: Boolean = runningUpstreams == 0
      private def allMessagesReady: Boolean = activeStreams.size == inputPorts
      private val messages: Array[Any] = new Array[Any](inputPorts)
      private var conflatesUpdates: Boolean = false
      private var latestDirty: Boolean = false
      private var completionPending: Boolean = false

      override def preStart(): Unit = {
        conflatesUpdates = materializer.system.settings.config
          .getString("pekko.stream.materializer.merge-latest.mode") match {
          case "latest" => true
          case "legacy" => false
          case mode     =>
            throw new IllegalArgumentException(
              s"pekko.stream.materializer.merge-latest.mode must be either 'latest' or 'legacy', but was '$mode'")
        }
        in.foreach(tryPull)
      }

      private def emitLatestIfPossible(): Unit = {
        if (latestDirty && allMessagesReady && isAvailable(out)) {
          push(out, buildElem(messages.asInstanceOf[Array[T]]))
          latestDirty = false
          if (completionPending) {
            completionPending = false
            completeStage()
          }
        }
      }

      private def finish(): Unit = {
        if (conflatesUpdates && latestDirty && allMessagesReady) {
          completionPending = true
          emitLatestIfPossible()
        } else completeStage()
      }

      in.zipWithIndex.foreach {
        case (input, index) =>
          setHandler(
            input,
            new InHandler {
              override def onPush(): Unit = {
                messages.update(index, grab(input))
                activeStreams.add(index)
                if (allMessagesReady) {
                  if (conflatesUpdates) {
                    latestDirty = true
                    emitLatestIfPossible()
                  } else emit(out, buildElem(messages.asInstanceOf[Array[T]]))
                }
                tryPull(input)
              }

              override def onUpstreamFinish(): Unit = {
                if (!eagerClose) {
                  runningUpstreams -= 1
                  if (upstreamsClosed) finish()
                } else finish()
              }
            })
      }

      override def onPull(): Unit = {
        val completing = completionPending
        emitLatestIfPossible()
        if (!completing) {
          var i = 0
          while (i < inputPorts) {
            if (!hasBeenPulled(in(i))) tryPull(in(i))
            i += 1
          }
        }
      }

      setHandler(out, this)
    }

  override def toString = "MergeLatest"
}
