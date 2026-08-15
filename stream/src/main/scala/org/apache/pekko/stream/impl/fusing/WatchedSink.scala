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
 * Implements `Sink.watchTermination`: wraps a sink that consists of a single [[GraphStageWithMaterializedValue]]
 * so that, in addition to the original materialized value, a `Future[Done]` is materialized that only completes
 * after the wrapped sink's `postStop` lifecycle hook has run.
 */
@InternalApi private[pekko] object WatchedSink {

  def apply[In, Mat, Mat2](sink: Sink[In, Mat], matF: (Mat, Future[Done]) => Mat2): Sink[In, Mat2] = {
    val builder = sink.traversalBuilder
    // use traversalSoFar rather than traversal, which would additionally wrap island and attribute
    // steps that the builder keeps separately and re-applies on access
    val steps = Vector.newBuilder[Traversal]
    flatten(builder.traversalSoFar, steps)
    val allSteps = steps.result()

    val moduleIndices = allSteps.indices.filter(i => allSteps(i).isInstanceOf[MaterializeAtomic])
    if (moduleIndices.size != 1)
      throw new IllegalArgumentException(
        s"Sink.watchTermination is only supported for sinks that consist of a single stage, but [$sink] consists " +
        s"of ${moduleIndices.size} stages. Composite sinks such as those created with Sink.combine or GraphDSL " +
        s"are not supported.")

    val moduleIndex = moduleIndices.head
    allSteps(moduleIndex) match {
      case MaterializeAtomic(module: GraphStageModule[SinkShape[In] @unchecked, Mat @unchecked], outToSlots)
          if outToSlots.isEmpty =>
        val prefixSteps = allSteps.take(moduleIndex)
        val suffixSteps = allSteps.drop(moduleIndex + 1)

        val watchedStage = new WatchedSinkStage[In, Mat, Mat2](module.stage, suffixSteps, matF)
        val watchedModule = GraphStageModule(module.shape, module.attributes, watchedStage)
        val newTraversal = (prefixSteps :+ (MaterializeAtomic(watchedModule, outToSlots): Traversal))
          .foldLeft(EmptyTraversal: Traversal)((traversal, step) => traversal.concat(step))

        new Sink(builder.copy(traversalSoFar = newTraversal), sink.shape)
      case other =>
        throw new IllegalArgumentException(
          s"Sink.watchTermination is only supported for sinks that consist of a single GraphStage, but [$sink] " +
          s"contains [$other].")
    }
  }

  private def flatten(traversal: Traversal, builder: scala.collection.mutable.Builder[Traversal, Vector[Traversal]])
      : Unit = traversal match {
    case EmptyTraversal        =>
    case Concat(first, second) =>
      flatten(first, builder)
      flatten(second, builder)
    case other => builder += other
  }

  /**
   * Replays the materialized value composition steps that followed the wrapped stage in the original
   * traversal, transforming the wrapped stage's materialized value into the materialized value the
   * original sink would have produced.
   */
  private[fusing] def runMatProgram(steps: Vector[Traversal], initial: Any): Any = {
    val stack = new java.util.ArrayDeque[Any](4)
    stack.addLast(initial)
    var i = 0
    while (i < steps.length) {
      steps(i) match {
        case Pop                  => stack.removeLast()
        case PushNotUsed          => stack.addLast(NotUsed)
        case transform: Transform => stack.addLast(transform(stack.removeLast()))
        case compose: Compose     =>
          val second = stack.removeLast()
          val first = stack.removeLast()
          stack.addLast(compose(first, second))
        case other =>
          throw new IllegalArgumentException(
            s"Sink.watchTermination encountered an unexpected materialized value composition step [$other]")
      }
      i += 1
    }
    stack.removeLast()
  }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class WatchedSinkStage[-In, Mat, Mat2](
    inner: GraphStageWithMaterializedValue[SinkShape[In], Mat],
    trailingMatProgram: Vector[Traversal],
    matF: (Mat, Future[Done]) => Mat2)
    extends GraphStageWithMaterializedValue[SinkShape[In], Mat2] {

  override val shape: SinkShape[In] = inner.shape

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Mat2) =
    logicAndMat(inheritedAttributes, null)

  private[pekko] override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes,
      materializer: Materializer): (GraphStageLogic, Mat2) =
    logicAndMat(inheritedAttributes, materializer)

  private def logicAndMat(inheritedAttributes: Attributes, materializer: Materializer): (GraphStageLogic, Mat2) = {
    val (innerLogic, innerMat) = inner.createLogicAndMaterializedValue(inheritedAttributes, materializer)
    val terminationPromise = Promise[Done]()
    val sinkMat = WatchedSink.runMatProgram(trailingMatProgram, innerMat).asInstanceOf[Mat]
    (new WatchedSinkLogic(innerLogic, inner, terminationPromise), matF(sinkMat, terminationPromise.future))
  }

  override def toString: String = s"WatchedSink($inner)"
}

/**
 * INTERNAL API
 *
 * A delegating [[GraphStageLogic]] that behaves exactly as the wrapped logic while completing the
 * termination promise only after the wrapped logic's `postStop` has run. The future is failed with
 * the upstream failure when the stream failed, and completed with success otherwise.
 */
@InternalApi private[pekko] final class WatchedSinkLogic(
    inner: GraphStageLogic,
    innerStage: GraphStageWithMaterializedValue[? <: Shape, ?],
    terminationPromise: Promise[Done])
    extends GraphStageLogic(inner.inCount, inner.outCount)
    with InHandler {

  private var terminationFailure: Throwable = _
  private var terminationSignalled = false

  // Completes the promise even if the interpreter finalizes the wrapped logic directly,
  // which happens when the wrapped stage terminates itself from an async callback.
  inner.setTerminationHook(() => completeTermination())

  // delegate all port handlers to the wrapped logic
  System.arraycopy(inner.handlers, 0, handlers, 0, handlers.length)

  // fuse the inlet handler into this logic to record why the stream terminated
  private val innerInHandler = inner.handlers(0).asInstanceOf[InHandler]
  handlers(0) = this

  override def onPush(): Unit =
    try innerInHandler.onPush()
    catch {
      case NonFatal(e) =>
        terminationFailure = e
        throw e
    }

  override def onUpstreamFinish(): Unit = {
    terminationSignalled = true
    try innerInHandler.onUpstreamFinish()
    catch {
      case NonFatal(e) =>
        terminationFailure = e
        throw e
    }
  }

  override def onUpstreamFailure(ex: Throwable): Unit = {
    terminationSignalled = true
    terminationFailure = ex
    try innerInHandler.onUpstreamFailure(ex)
    catch {
      case NonFatal(e) =>
        terminationFailure = e
        throw e
    }
  }

  private[stream] override def interpreter_=(gi: GraphInterpreter): Unit = {
    super.interpreter_=(gi)
    inner.interpreter_=(gi)
  }

  protected[stream] override def beforePreStart(): Unit = {
    inner.stageId = stageId
    inner.attributes = attributes
    inner.originalStage = OptionVal.Some(innerStage)
    // mirror the port wiring so that the wrapped logic can interact with the interpreter
    System.arraycopy(portToConn, 0, inner.portToConn, 0, portToConn.length)
    inner.beforePreStart()
  }

  override def preStart(): Unit =
    try inner.preStart()
    catch {
      case NonFatal(e) =>
        terminationFailure = e
        throw e
    }

  override def postStop(): Unit = {
    try inner.postStop()
    finally completeTermination()
  }

  protected[stream] override def afterPostStop(): Unit = {
    inner.afterPostStop()
    completeTermination()
  }

  // completeTermination may be invoked more than once (from postStop, afterPostStop and the
  // termination hook), the promise only completes on the first invocation
  private def completeTermination(): Unit = {
    val failure = terminationFailure
    if (failure ne null) terminationPromise.tryFailure(failure)
    else
      upstreamFailureFromConnection match {
        case OptionVal.Some(ex) => terminationPromise.tryFailure(ex)
        case _                  =>
          if (!terminationSignalled && isAbruptTermination)
            terminationPromise.tryFailure(new AbruptStageTerminationException(this))
          else terminationPromise.trySuccess(Done)
      }
  }

  // If the wrapped stage swapped its inlet handler after materialization, failures no longer pass
  // through the fused handler above; the failure remains visible on the connection slot until
  // after this stage has been finalized.
  private def upstreamFailureFromConnection: OptionVal[Throwable] = {
    val connection = portToConn(0)
    if (connection ne null)
      connection.slot match {
        case GraphInterpreter.Failed(ex, _) => OptionVal.Some(ex)
        case _                              => OptionVal.None
      }
    else OptionVal.None
  }

  // postStop ran without any side of the inlet connection ever being closed, so no completion,
  // failure or cancellation signal reached the wrapped sink
  private def isAbruptTermination: Boolean = {
    val connection = portToConn(0)
    (connection ne null) && (connection.portState & (GraphInterpreter.InClosed | GraphInterpreter.OutClosed)) == 0
  }

  override def toString: String = s"WatchedSink($inner)"
}
