/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.impl.fusing

import scala.collection.immutable
import scala.concurrent.Future
import scala.util.{ Failure, Try }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, FlowShape, Graph, Inlet, Outlet, SourceShape, SubscriptionWithCancelException }
import pekko.stream.impl.{ Buffer => BufferImpl, FailedSource, JavaStreamSource, TraversalBuilder }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.fusing.GraphStages.{ FutureSource, RepeatSource, SingleSource }
import pekko.stream.impl.fusing.InflightSources._
import pekko.stream.scaladsl.Source
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class FlattenConcat[T, M](parallelism: Int)
    extends GraphStage[FlowShape[Graph[SourceShape[T], M], T]] {
  require(parallelism >= 1, "parallelism should >= 1")
  private val in = Inlet[Graph[SourceShape[T], M]]("flattenConcat.in")
  private val out = Outlet[T]("flattenConcat.out")

  override def initialAttributes: Attributes = DefaultAttributes.flattenConcat
  override val shape: FlowShape[Graph[SourceShape[T], M], T] = FlowShape(in, out)
  override def createLogic(enclosingAttributes: Attributes) = {
    object FlattenConcatLogic extends GraphStageLogic(shape) with InHandler with OutHandler {
      // InflightSource[T] or SingleSource[T]
      // AnyRef here to avoid lift the SingleSource[T] to InflightSource[T]
      private var queue: BufferImpl[AnyRef] = _
      private val invokeCb: InflightSource[T] => Unit =
        getAsyncCallback[InflightSource[T]](futureSourceCompleted).invoke

      override def preStart(): Unit = queue = BufferImpl(parallelism, enclosingAttributes)

      private def futureSourceCompleted(futureSource: InflightSource[T]): Unit = {
        if (queue.peek() eq futureSource) {
          if (isAvailable(out) && futureSource.hasNext) {
            // Success(null) is filtered out via InflightPendingFutureSource.hasNext to stay
            // consistent with GraphStages.FutureSource (which treats Success(null) as completion).
            push(out, futureSource.next())
            if (futureSource.isClosed) {
              handleCurrentSourceClosed(futureSource)
            }
          } else if (futureSource.isClosed) {
            handleCurrentSourceClosed(futureSource)
          }
        } // else just ignore, it will be picked up by onPull
      }

      override def onPush(): Unit = {
        addSource(grab(in))
        // must try pull after addSource to avoid queue overflow
        if (!queue.isFull) { // try to keep the maximum parallelism
          tryPull(in)
        }
      }

      override def onUpstreamFinish(): Unit = if (queue.isEmpty) completeStage()

      override def onUpstreamFailure(ex: Throwable): Unit = {
        super.onUpstreamFailure(ex)
        cancelInflightSources(SubscriptionWithCancelException.NoMoreElementsNeeded)
      }

      override def onPull(): Unit = {
        // purge if possible
        queue.peek() match {
          case src: SingleSource[T] @unchecked =>
            push(out, src.elem)
            removeSource()
          case src: InflightSource[T] @unchecked => pushOut(src)
          case null                              => // queue is empty
            if (!hasBeenPulled(in)) {
              tryPull(in)
            } else if (isClosed(in)) {
              completeStage()
            }
          case _ => throw new IllegalStateException("Should not reach here.")
        }
      }

      private def pushOut(src: InflightSource[T]): Unit = {
        if (src.hasNext) {
          push(out, src.next())
          if (src.isClosed) {
            handleCurrentSourceClosed(src)
          }
        } else if (src.isClosed) {
          handleCurrentSourceClosed(src)
        } else {
          src.tryPull()
        }
      }

      private def handleCurrentSourceClosed(source: InflightSource[T]): Unit = {
        source.failure match {
          case Some(cause) => onUpstreamFailure(cause)
          case None        => removeSource(source)
        }
      }

      override def onDownstreamFinish(cause: Throwable): Unit = {
        super.onDownstreamFinish(cause)
        cancelInflightSources(cause)
      }

      private def cancelInflightSources(cause: Throwable): Unit = {
        if (queue.nonEmpty) {
          var source = queue.dequeue()
          while ((source ne null) && (source.isInstanceOf[InflightSource[T] @unchecked])) {
            source.asInstanceOf[InflightSource[T]].cancel(cause)
            source = queue.dequeue()
          }
        }
      }

      private def addSource(singleSource: SingleSource[T]): Unit = {
        if (isAvailable(out) && queue.isEmpty) {
          push(out, singleSource.elem)
        } else {
          queue.enqueue(singleSource)
        }
      }

      private def addSourceElements(iterator: Iterator[T]): Unit = {
        val inflightSource = new InflightIteratorSource[T](iterator)
        if (isAvailable(out) && queue.isEmpty) {
          if (inflightSource.hasNext) {
            push(out, inflightSource.next())
            if (inflightSource.hasNext) {
              queue.enqueue(inflightSource)
            }
          }
        } else {
          queue.enqueue(inflightSource)
        }
      }

      private def addRangeSource(range: immutable.Range): Unit = {
        val inflightSource = new InflightRangeSource[T](range)
        if (isAvailable(out) && queue.isEmpty) {
          if (inflightSource.hasNext) {
            push(out, inflightSource.next())
            if (inflightSource.hasNext)
              queue.enqueue(inflightSource)
          }
        } else if (inflightSource.hasNext) {
          queue.enqueue(inflightSource)
        }
      }

      private def addRepeatSource(elem: T): Unit = {
        val inflightSource = new InflightRepeatSource[T](elem)
        if (isAvailable(out) && queue.isEmpty)
          push(out, elem)
        queue.enqueue(inflightSource)
      }

      private def addJavaStreamSource(javaStream: JavaStreamSource[T, _]): Unit = {
        val inflightSource = new InflightJavaStreamSource[T](javaStream.open)
        if (isAvailable(out) && queue.isEmpty) {
          if (inflightSource.hasNext) {
            push(out, inflightSource.next())
            if (inflightSource.hasNext) {
              queue.enqueue(inflightSource)
            }
          }
        } else if (inflightSource.hasNext) {
          queue.enqueue(inflightSource)
        }
      }

      private def addCompletedFutureElem(elem: Try[T]): Unit = elem match {
        // DO NOT CHANGE
        // WHY: GraphStages.FutureSource treats Success(null) as completion-without-element
        // (see FutureSource.handle: `case Success(null) => completeStage()`). The fast path
        // here MUST mirror that — pushing null would violate Reactive Streams' no-null rule
        // and diverge from the materialized FutureSource behaviour.
        // How to apply: keep this branch in sync with FutureSource semantics; if FutureSource
        // ever changes how it treats Success(null), update here too.
        case scala.util.Success(null)  => // empty inner source: discard, slot is freed when caller dequeues
        case scala.util.Success(value) =>
          if (isAvailable(out) && queue.isEmpty) {
            push(out, value)
          } else {
            queue.enqueue(new InflightCompletedFutureSource(elem))
          }
        case scala.util.Failure(ex) =>
          if (isAvailable(out) && queue.isEmpty) {
            onUpstreamFailure(ex)
          } else {
            queue.enqueue(new InflightCompletedFutureSource(elem))
          }
      }

      private def addPendingFutureElem(future: Future[T]): Unit = {
        val inflightSource = new InflightPendingFutureSource[T](invokeCb)
        future.onComplete(inflightSource)(scala.concurrent.ExecutionContext.parasitic)
        queue.enqueue(inflightSource)
      }

      private def attachAndMaterializeSource(source: Graph[SourceShape[T], M]): Unit = {
        object inflightSource extends InflightSource[T] { self =>
          private val sinkIn = new SubSinkInlet[T]("FlattenConcatSink")
          private var upstreamFailure = Option.empty[Throwable]
          sinkIn.setHandler(new InHandler {
            override def onPush(): Unit = {
              if (isAvailable(out) && (queue.peek() eq self)) {
                push(out, sinkIn.grab())
              }
            }
            override def onUpstreamFinish(): Unit = if (!sinkIn.isAvailable) removeSource(self)
            override def onUpstreamFailure(ex: Throwable): Unit = {
              upstreamFailure = Some(ex)
              // if it's the current emitting source, fail the stage
              if (queue.peek() eq self) {
                super.onUpstreamFailure(ex)
              } // else just mark the source as failed
            }
          })

          final override def materialize(): Unit = {
            val graph = Source.fromGraph(source).to(sinkIn.sink)
            interpreter.subFusingMaterializer.materialize(graph, defaultAttributes = enclosingAttributes)
          }
          final override def cancel(cause: Throwable): Unit = sinkIn.cancel(cause)
          final override def hasNext: Boolean = sinkIn.isAvailable
          final override def isClosed: Boolean = sinkIn.isClosed
          final override def failure: Option[Throwable] = upstreamFailure
          final override def next(): T = sinkIn.grab()
          final override def tryPull(): Unit = if (!sinkIn.isClosed && !sinkIn.hasBeenPulled) sinkIn.pull()
        }
        if (isAvailable(out) && queue.isEmpty) {
          // this is the first one, pull
          inflightSource.tryPull()
        }
        queue.enqueue(inflightSource)
        inflightSource.materialize()
      }

      private def addSource(source: Graph[SourceShape[T], M]): Unit = {
        TraversalBuilder.getValuePresentedSource(source) match {
          case OptionVal.Some(graph) =>
            graph match {
              case single: SingleSource[T] @unchecked       => addSource(single)
              case futureSource: FutureSource[T] @unchecked =>
                val future = futureSource.future
                future.value match {
                  case Some(elem) => addCompletedFutureElem(elem)
                  case None       => addPendingFutureElem(future)
                }
              case iterable: IterableSource[T] @unchecked                   => addSourceElements(iterable.elements.iterator)
              case iterator: IteratorSource[T] @unchecked                   => addSourceElements(iterator.createIterator())
              case range: RangeSource[T] @unchecked                         => addRangeSource(range.range)
              case repeat: RepeatSource[T] @unchecked                       => addRepeatSource(repeat.elem)
              case javaStream: JavaStreamSource[T, _] @unchecked            => addJavaStreamSource(javaStream)
              case failed: FailedSource[T] @unchecked                       => addCompletedFutureElem(Failure(failed.failure))
              case maybeEmpty if TraversalBuilder.isEmptySource(maybeEmpty) => // Empty source is discarded
              case _                                                        => attachAndMaterializeSource(source)
            }
          case _ => attachAndMaterializeSource(source)
        }

      }

      private def removeSource(): Unit = {
        queue.dequeue()
        pullIfNeeded()
      }

      private def removeSource(source: InflightSource[T]): Unit = {
        if (source eq queue.peek()) {
          // only dequeue if it's the current emitting source
          queue.dequeue()
          pullIfNeeded()
        } // not the head source, just ignore
      }

      private def pullIfNeeded(): Unit = {
        if (isClosed(in)) {
          if (queue.isEmpty) {
            completeStage()
          } else {
            tryPullNextSourceInQueue()
          }
        } else {
          if (queue.nonEmpty) {
            tryPullNextSourceInQueue()
          }
          if (!hasBeenPulled(in)) {
            tryPull(in)
          }
        }
      }

      private def tryPullNextSourceInQueue(): Unit = queue.peek() match {
        case src: SingleSource[T] @unchecked =>
          // DO NOT CHANGE
          // WHY: A previous head InflightSource may complete without emitting (e.g. a
          // pending future resolving to Success(null), which we treat as completion-
          // without-element to mirror GraphStages.FutureSource). If demand is still
          // pending and the new head is a SingleSource, no further onPull will fire
          // when in is closed — the stage would stall. Push directly here.
          if (isAvailable(out)) {
            push(out, src.elem)
            removeSource()
          }
        case src: InflightSource[T] @unchecked => src.tryPull()
        case _                                 => // queue empty or unexpected: nothing to pull
      }

      setHandlers(in, out, this)
    }

    FlattenConcatLogic
  }

  override def toString: String = s"FlattenConcat(parallelism=$parallelism)"
}
