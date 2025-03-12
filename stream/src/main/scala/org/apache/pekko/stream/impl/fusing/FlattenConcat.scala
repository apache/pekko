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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.scaladsl.Source
import pekko.stream.{ Attributes, FlowShape, Graph, Inlet, Outlet, SourceShape, SubscriptionWithCancelException }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.{ Buffer => BufferImpl, FailedSource, JavaStreamSource, TraversalBuilder }
import pekko.stream.impl.fusing.GraphStages.{ FutureSource, SingleSource }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.OptionVal

import scala.concurrent.Future
import scala.util.{ Failure, Try }

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object FlattenConcat {
  private sealed abstract class InflightSource[T] {
    def hasNext: Boolean
    def next(): T
    def tryPull(): Unit
    def cancel(cause: Throwable): Unit
    def isClosed: Boolean
    def hasFailed: Boolean = failure.isDefined
    def failure: Option[Throwable] = None
    def materialize(): Unit = ()
  }

  private final class InflightIteratorSource[T](iterator: Iterator[T]) extends InflightSource[T] {
    override def hasNext: Boolean = iterator.hasNext
    override def next(): T = iterator.next()
    override def tryPull(): Unit = ()
    override def cancel(cause: Throwable): Unit = ()
    override def isClosed: Boolean = !hasNext
  }

  private final class InflightCompletedFutureSource[T](result: Try[T]) extends InflightSource[T] {
    private var _hasNext = result.isSuccess
    override def hasNext: Boolean = _hasNext
    override def next(): T = {
      if (_hasNext) {
        _hasNext = false
        result.get
      } else throw new NoSuchElementException("next called after completion")
    }
    override def hasFailed: Boolean = result.isFailure
    override def failure: Option[Throwable] = result.failed.toOption
    override def tryPull(): Unit = ()
    override def cancel(cause: Throwable): Unit = ()
    override def isClosed: Boolean = true
  }

  private final class InflightPendingFutureSource[T](cb: InflightSource[T] => Unit)
      extends InflightSource[T]
      with (Try[T] => Unit) {
    private var result: Try[T] = MapAsync.NotYetThere
    private var consumed = false
    override def apply(result: Try[T]): Unit = {
      this.result = result
      cb(this)
    }
    override def hasNext: Boolean = (result ne MapAsync.NotYetThere) && !consumed && result.isSuccess
    override def next(): T = {
      if (!consumed) {
        consumed = true
        result.get
      } else throw new NoSuchElementException("next called after completion")
    }
    override def hasFailed: Boolean = (result ne MapAsync.NotYetThere) && result.isFailure
    override def failure: Option[Throwable] = if (result eq MapAsync.NotYetThere) None else result.failed.toOption
    override def tryPull(): Unit = ()
    override def cancel(cause: Throwable): Unit = ()
    override def isClosed: Boolean = consumed || hasFailed
  }
}

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
      import FlattenConcat._
      // InflightSource[T] or SingleSource[T]
      // AnyRef here to avoid lift the SingleSource[T] to InflightSource[T]
      private var queue: BufferImpl[AnyRef] = _
      private val invokeCb: InflightSource[T] => Unit =
        getAsyncCallback[InflightSource[T]](futureSourceCompleted).invoke

      override def preStart(): Unit = queue = BufferImpl(parallelism, enclosingAttributes)

      private def futureSourceCompleted(futureSource: InflightSource[T]): Unit = {
        if (queue.peek() eq futureSource) {
          if (isAvailable(out) && futureSource.hasNext) {
            push(out, futureSource.next()) // TODO should filter out the `null` here?
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
          case null => // queue is empty
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

      private def addCompletedFutureElem(elem: Try[T]): Unit = {
        if (isAvailable(out) && queue.isEmpty) {
          elem match {
            case scala.util.Success(value) => push(out, value)
            case scala.util.Failure(ex)    => onUpstreamFailure(ex)
          }
        } else {
          queue.enqueue(new InflightCompletedFutureSource(elem))
        }
      }

      private def addPendingFutureElem(future: Future[T]): Unit = {
        val inflightSource = new InflightPendingFutureSource[T](invokeCb)
        future.onComplete(inflightSource)(pekko.dispatch.ExecutionContexts.parasitic)
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
              case single: SingleSource[T] @unchecked => addSource(single)
              case futureSource: FutureSource[T] @unchecked =>
                val future = futureSource.future
                future.value match {
                  case Some(elem) => addCompletedFutureElem(elem)
                  case None       => addPendingFutureElem(future)
                }
              case iterable: IterableSource[T] @unchecked => addSourceElements(iterable.elements.iterator)
              case javaStream: JavaStreamSource[T, _] @unchecked =>
                import pekko.util.ccompat.JavaConverters._
                addSourceElements(javaStream.open().iterator.asScala)
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

      private def tryPullNextSourceInQueue(): Unit = {
        // pull the new emitting source
        val nextSource = queue.peek()
        if (nextSource.isInstanceOf[InflightSource[T] @unchecked]) {
          nextSource.asInstanceOf[InflightSource[T]].tryPull()
        }
      }

      setHandlers(in, out, this)
    }

    FlattenConcatLogic
  }

  override def toString: String = s"FlattenConcat(parallelism=$parallelism)"
}
