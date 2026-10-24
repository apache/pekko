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
import scala.util.{ Failure, Success }

import org.apache.pekko.{ Done, NotUsed }
import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.stream._
import org.apache.pekko.stream.impl._
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.stage._
import org.apache.pekko.util.OptionVal

/**
 * INTERNAL API
 *
 * Observes every GraphStage in the sink, including stages in separate async islands.
 * The materialized-value stack is local to each traversal, so failed, concurrent and
 * reentrant materializations never share a tracker.
 */
@InternalApi private[pekko] object WatchedSink {

  def apply[In, Mat, Mat2](sink: Sink[In, Mat], matF: (Mat, Future[Done]) => Mat2): Sink[In, Mat2] = {
    val builder = sink.traversalBuilder
    // A sink has no unwired outputs, so its entire graph is in traversalSoFar.
    val steps = Vector.newBuilder[Traversal]
    var pending = List(builder.traversalSoFar)
    while (pending.nonEmpty) {
      val step = pending.head
      pending = pending.tail
      step match {
        case EmptyTraversal        =>
        case Concat(first, second) => pending = first :: second :: pending
        case other                 => steps += other
      }
    }
    val allSteps = steps.result()
    val stageCount = allSteps.count {
      case MaterializeAtomic(_: GraphStageModule[?, ?], _) => true
      case MaterializeAtomic(other, _)                     =>
        throw new IllegalArgumentException(
          s"Sink.watchTermination is only supported for sinks built from GraphStages, but [$sink] contains [$other].")
      case _ => false
    }
    require(stageCount > 0, "Sink.watchTermination requires at least one GraphStage")

    // Keep the original materialized-value stack inside a single traversal-local value.
    // Each atomic stage adds its original value to that stack and joins the same tracker.
    val start = PushNotUsed.concat(Transform((_: Any) => new Materialization(stageCount)))
    val traversal = allSteps.foldLeft(start) {
      case (acc, MaterializeAtomic(module: GraphStageModule[?, ?], outToSlots)) =>
        val stage = new TerminationReporterStage(
          module.stage.asInstanceOf[GraphStageWithMaterializedValue[Shape, Any]])
        acc.concat(MaterializeAtomic(GraphStageModule(module.shape, module.attributes, stage), outToSlots))
          .concat(Compose((state: Any, value: Any) => {
            val materialization = state.asInstanceOf[Materialization]
            val (logic, mat) = value.asInstanceOf[(TerminationReporterLogic, Any)]
            logic.tracker = materialization.tracker
            materialization.values.addLast(mat)
            materialization
          }))
      case (acc, PushNotUsed)          => acc.concat(update(_.addLast(NotUsed)))
      case (acc, Pop)                  => acc.concat(update { values => values.removeLast(); () })
      case (acc, transform: Transform) =>
        acc.concat(update { values => values.addLast(transform(values.removeLast())) })
      case (acc, compose: Compose) =>
        acc.concat(update { values =>
          val second = values.removeLast()
          val first = values.removeLast()
          values.addLast(compose(first, second))
        })
      case (acc, other) => acc.concat(other)
    }
    val result = traversal.concat(Transform { (value: Any) =>
      val materialization = value.asInstanceOf[Materialization]
      matF(materialization.values.removeLast().asInstanceOf[Mat], materialization.tracker.future)
    })
    new Sink(builder.copy(traversalSoFar = result), sink.shape)
  }

  private class Materialization(stageCount: Int) {
    val tracker = new TerminationTracker(stageCount)
    val values = new java.util.ArrayDeque[Any]()
  }

  private def update(f: java.util.ArrayDeque[Any] => Unit): Transform = Transform { (value: Any) =>
    f(value.asInstanceOf[Materialization].values)
    value
  }
}

/** INTERNAL API */
@InternalApi private[pekko] final class TerminationTracker(stageCount: Int) {
  private var remaining = stageCount
  private var failure: Throwable = _
  private val promise = Promise[Done]()
  val future: Future[Done] = promise.future

  def stageStopped(cause: Throwable): Unit = {
    val result = synchronized {
      if ((failure eq null) && (cause ne null)) failure = cause
      remaining -= 1
      if (remaining == 0) Some(if (failure eq null) Success(Done) else Failure(failure))
      else None
    }
    result.foreach(promise.tryComplete)
  }
}

/** INTERNAL API */
@InternalApi private[pekko] final class TerminationReporterStage(inner: GraphStageWithMaterializedValue[Shape, Any])
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
    val logic = new TerminationReporterLogic(innerLogic, inner)
    (logic, (logic, innerMat))
  }

  override def toString: String = s"WatchedSink($inner)"
}

/**
 * INTERNAL API
 *
 * Delegates lifecycle hooks, leaving port handlers unchanged. The interpreter records
 * failures on terminal paths and finalizes the registered wrapper even when an async
 * callback belongs to the inner logic. No extra handler dispatch is needed per element.
 */
@InternalApi private[pekko] final class TerminationReporterLogic(
    inner: GraphStageLogic,
    innerStage: GraphStageWithMaterializedValue[? <: Shape, ?])
    extends GraphStageLogic(inner.inCount, inner.outCount) {

  var tracker: TerminationTracker = _
  private var failure: Throwable = _

  System.arraycopy(inner.handlers, 0, handlers, 0, handlers.length)

  private[stream] override def interpreter_=(gi: GraphInterpreter): Unit = {
    super.interpreter_=(gi)
    inner.interpreter_=(gi)
  }

  protected[stream] override def beforePreStart(): Unit = {
    inner.stageId = stageId
    inner.attributes = attributes
    inner.originalStage = OptionVal.Some(innerStage)
    System.arraycopy(portToConn, 0, inner.portToConn, 0, portToConn.length)
    // Default handlers use the interpreter's activeStage. Keep the actual logic as
    // the connection owner so completion and failure also clean up its substreams.
    var i = 0
    while (i < portToConn.length) {
      val connection = portToConn(i)
      if (connection.inOwner eq this) connection.inOwner = inner
      if (connection.outOwner eq this) connection.outOwner = inner
      i += 1
    }
    inner.beforePreStart()
  }

  def underlyingLogic: GraphStageLogic = inner match {
    case watched: TerminationReporterLogic => watched.underlyingLogic
    case _                                 => inner
  }

  override def preStart(): Unit = inner.preStart()
  override def postStop(): Unit = inner.postStop()
  protected[stream] override def afterPostStop(): Unit = inner.afterPostStop()

  def recordFailure(cause: Throwable): Unit = {
    if ((failure eq null) && !cause.isInstanceOf[SubscriptionWithCancelException.NonFailureCancellation])
      failure = cause
    inner match {
      case watched: TerminationReporterLogic => watched.recordFailure(cause)
      case _                                 =>
    }
  }

  def reportTermination(): Unit = {
    inner match {
      case watched: TerminationReporterLogic => watched.reportTermination()
      case _                                 =>
    }
    tracker.stageStopped(failure)
  }

  override def toString: String = s"WatchedSink($inner)"
}
