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

import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal

import org.apache.pekko.Done
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.stream._
import org.apache.pekko.stream.impl._
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.stage._
import org.apache.pekko.util.OptionVal

/**
 * INTERNAL API
 *
 * Implements `Sink.watchTermination`: wraps every [[GraphStageWithMaterializedValue]] in the sink's
 * traversal so that a shared `Future[Done]` is materialized that only completes after all wrapped
 * stages' `postStop` lifecycle hooks have run.
 */
@InternalApi private[pekko] object WatchedSink {

  def apply[In, Mat, Mat2](sink: Sink[In, Mat], matF: (Mat, Future[Done]) => Mat2): Sink[In, Mat2] = {
    val builder = sink.traversalBuilder
    val steps = Vector.newBuilder[Traversal]
    flatten(builder.traversalSoFar, steps)
    val allSteps = steps.result()

    val stageCount = allSteps.count {
      case MaterializeAtomic(_: GraphStageModule[?, ?], _) => true
      case _                                               => false
    }

    if (stageCount == 0)
      throw new IllegalArgumentException(
        s"Sink.watchTermination is only supported for sinks that contain at least one GraphStage, but [$sink] " +
        s"contains none.")

    allSteps.foreach {
      case MaterializeAtomic(_: GraphStageModule[?, ?], _) =>
      case MaterializeAtomic(other, _)                     =>
        throw new IllegalArgumentException(
          s"Sink.watchTermination is only supported for sinks built from GraphStages, but [$sink] " +
          s"contains [$other].")
      case _ =>
    }

    // Per-materialization holder: the traversal walk is sequential, so the first stage
    // creates the tracker and subsequent stages reuse it within the same materialization.
    val holder = new TrackerHolder(stageCount)

    val newSteps: Vector[Traversal] = allSteps.map {
      case MaterializeAtomic(module: GraphStageModule[?, ?], outToSlots) =>
        val reporterStage = new TerminationReporterStage(
          module.stage.asInstanceOf[GraphStageWithMaterializedValue[Shape, Any]], holder)
        MaterializeAtomic(
          GraphStageModule(module.shape, module.attributes,
            reporterStage.asInstanceOf[GraphStageWithMaterializedValue[Shape, Any]]),
          outToSlots): Traversal
      case other => other
    }

    val matFStep: Traversal =
      Transform(((mat: Any) => {
            val t = holder.tracker
            val result = matF(mat.asInstanceOf[Mat], t.future)
            holder.reset()
            result
          }).asInstanceOf[TraversalBuilder.AnyFunction1])

    val newTraversal = (newSteps :+ matFStep)
      .foldLeft(EmptyTraversal: Traversal)((traversal, step) => traversal.concat(step))

    new Sink(builder.copy(traversalSoFar = newTraversal), sink.shape)
  }

  private def flatten(traversal: Traversal, builder: scala.collection.mutable.Builder[Traversal, Vector[Traversal]])
      : Unit = traversal match {
    case EmptyTraversal        =>
    case Concat(first, second) =>
      flatten(first, builder)
      flatten(second, builder)
    case other => builder += other
  }
}

/**
 * INTERNAL API
 *
 * Provides a fresh [[TerminationTracker]] per materialization. A single [[TrackerHolder]] instance is
 * shared by every materialization of the same `Sink` blueprint (it is captured once, when
 * `Sink.watchTermination` builds the traversal), so it must tolerate concurrent materializations of that
 * blueprint from different threads, which is a supported usage pattern for stream blueprints.
 *
 * The traversal walk for a single materialization is always sequential and confined to the thread that
 * calls `materialize`/`run`/`runWith`: stages call `tracker` (lazy-creating on first access on that thread),
 * and the trailing Transform step calls `reset()` after capturing the future. A plain mutable field would
 * therefore race across concurrent materializations; using a [[ThreadLocal]] instead scopes the tracker to
 * the materializing thread so concurrent materializations never observe each other's state.
 */
@InternalApi private[pekko] final class TrackerHolder(stageCount: Int) {
  private val threadLocalTracker = new ThreadLocal[TerminationTracker]

  def tracker: TerminationTracker = {
    var t = threadLocalTracker.get()
    if (t eq null) {
      t = new TerminationTracker(stageCount)
      threadLocalTracker.set(t)
    }
    t
  }

  def reset(): Unit = threadLocalTracker.remove()
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class TerminationTracker(stageCount: Int) {
  private var remaining = stageCount
  private var _failure: Throwable = _
  private var _sawSignal: Boolean = false
  private var _anyConnectionClosed: Boolean = false
  private val terminationPromise = Promise[Done]()

  val future: Future[Done] = terminationPromise.future

  def stageStopped(failure: Throwable, sawSignal: Boolean, connectionClosed: Boolean, logic: GraphStageLogic): Unit =
    synchronized {
      if (failure ne null) _failure = failure
      if (sawSignal) _sawSignal = true
      if (connectionClosed) _anyConnectionClosed = true
      remaining -= 1
      if (remaining == 0) {
        if (_failure ne null) terminationPromise.tryFailure(_failure)
        else if (!_sawSignal && !_anyConnectionClosed)
          terminationPromise.tryFailure(new AbruptStageTerminationException(logic))
        else terminationPromise.trySuccess(Done)
      }
    }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class TerminationReporterStage(
    inner: GraphStageWithMaterializedValue[Shape, Any],
    holder: TrackerHolder)
    extends GraphStageWithMaterializedValue[Shape, Any] {

  override val shape: Shape = inner.shape

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Any) =
    logicAndMat(inheritedAttributes, null)

  private[pekko] override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes,
      materializer: Materializer): (GraphStageLogic, Any) =
    logicAndMat(inheritedAttributes, materializer)

  private def logicAndMat(inheritedAttributes: Attributes, materializer: Materializer): (GraphStageLogic, Any) = {
    val (innerLogic, innerMat) =
      if (materializer eq null) inner.createLogicAndMaterializedValue(inheritedAttributes)
      else inner.createLogicAndMaterializedValue(inheritedAttributes, materializer)
    (new TerminationReporterLogic(innerLogic, inner, holder.tracker), innerMat)
  }

  override def toString: String = s"WatchedSink($inner)"
}

/**
 * INTERNAL API
 *
 * Delegates all behavior to the wrapped logic, reporting termination to a shared
 * [[TerminationTracker]] after the wrapped logic's `postStop` has run.
 *
 * Input handlers delegate dynamically via `inner.handlers(idx)` so that stages which swap
 * handlers after materialization (e.g. LazySink) are handled correctly. The try-catch has
 * zero JIT cost (exception-table only) and is required to capture failures from leaf stages
 * whose handler exceptions would not otherwise appear on any connection slot.
 */
@InternalApi private[pekko] final class TerminationReporterLogic(
    inner: GraphStageLogic,
    innerStage: GraphStageWithMaterializedValue[? <: Shape, ?],
    tracker: TerminationTracker)
    extends GraphStageLogic(inner.inCount, inner.outCount) {

  private var terminationFailure: Throwable = _
  private var sawTerminationSignal = false
  private var reported = false

  // Fires when the interpreter finalizes the inner logic directly (e.g. completeStage from async callback)
  inner.setTerminationHook(() => reportTermination())

  System.arraycopy(inner.handlers, 0, handlers, 0, handlers.length)

  private var i = 0
  while (i < inCount) {
    val idx = i
    handlers(idx) = new InHandler {
      override def onPush(): Unit =
        try inner.handlers(idx).asInstanceOf[InHandler].onPush()
        catch {
          case NonFatal(e) => terminationFailure = e; throw e
        }

      override def onUpstreamFinish(): Unit = {
        sawTerminationSignal = true
        try inner.handlers(idx).asInstanceOf[InHandler].onUpstreamFinish()
        catch {
          case NonFatal(e) => terminationFailure = e; throw e
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        sawTerminationSignal = true
        if (terminationFailure eq null) terminationFailure = ex
        try inner.handlers(idx).asInstanceOf[InHandler].onUpstreamFailure(ex)
        catch {
          case NonFatal(e) => terminationFailure = e; throw e
        }
      }
    }
    i += 1
  }

  private[stream] override def interpreter_=(gi: GraphInterpreter): Unit = {
    super.interpreter_=(gi)
    inner.interpreter_=(gi)
  }

  protected[stream] override def beforePreStart(): Unit = {
    inner.stageId = stageId
    inner.attributes = attributes
    inner.originalStage = OptionVal.Some(innerStage)
    System.arraycopy(portToConn, 0, inner.portToConn, 0, portToConn.length)
    inner.beforePreStart()
  }

  override def preStart(): Unit =
    try inner.preStart()
    catch {
      case NonFatal(e) => terminationFailure = e; throw e
    }

  override def postStop(): Unit = {
    try inner.postStop()
    finally reportTermination()
  }

  protected[stream] override def afterPostStop(): Unit = inner.afterPostStop()

  private def reportTermination(): Unit = {
    if (!reported) {
      reported = true
      var connectionFailure: Throwable = null
      var connectionClosed = false
      var j = 0
      while (j < portToConn.length) {
        val connection = portToConn(j)
        if (connection ne null) {
          connection.slot match {
            case GraphInterpreter.Failed(ex, _)    => if (connectionFailure eq null) connectionFailure = ex
            case GraphInterpreter.Cancelled(cause) =>
              if ((connectionFailure eq null) &&
                !cause.isInstanceOf[SubscriptionWithCancelException.NonFailureCancellation])
                connectionFailure = cause
            case _ =>
          }
          if ((connection.portState & (GraphInterpreter.InClosed | GraphInterpreter.OutClosed)) != 0)
            connectionClosed = true
        }
        j += 1
      }
      val failure = if (terminationFailure ne null) terminationFailure else connectionFailure
      tracker.stageStopped(failure, sawTerminationSignal, connectionClosed, this)
    }
  }

  override def toString: String = s"WatchedSink($inner)"
}
