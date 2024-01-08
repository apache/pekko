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

import org.apache.pekko.stream.ActorAttributes.SupervisionStrategy
import org.apache.pekko.stream.Attributes.SourceLocation
import org.apache.pekko.stream.impl.Stages.DefaultAttributes
import org.apache.pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import org.apache.pekko.stream._

import scala.util.control.NonFatal

private[pekko] class UnsafeTransformUnordered[In, Out](
    parallelism: Int,
    transform: (In, StreamCollector[Out]) => Unit)
    extends GraphStage[FlowShape[In, Out]] {
  private val in = Inlet[In]("UnsafeTransformOrdered.in")
  private val out = Outlet[Out]("UnsafeTransformOrdered.out")

  override def initialAttributes = DefaultAttributes.mapAsyncUnordered and SourceLocation.forLambda(transform)

  override val shape = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler with UnsafeStreamCollector[Out] {
      override def toString = s"UnsafeTransformOrdered.Logic(inFlight=$inFlight, buffer=$buffer)"
      import org.apache.pekko.stream.impl.{ Buffer => BufferImpl }

      private val decider =
        inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private var inFlight = 0
      private var buffer: org.apache.pekko.stream.impl.Buffer[Out] = _

      import StreamCollector._

      private val callback: StreamCollectorCommand => Unit = getAsyncCallback[StreamCollectorCommand](handle).invoke

      override def emitSync(value: Out): Unit = handle(EmitNext(value))
      override def failSync(throwable: Throwable): Unit = handle(Fail(throwable))
      override def completeSync(): Unit = completeStage()

      // TODO check permit
      override final def emit(value: Out): Unit = callback(EmitNext(value))
      override final def fail(throwable: Throwable): Unit = callback(Fail(throwable))
      override final def complete(): Unit = callback(Complete)

      //
      private[this] def todo: Int = inFlight + buffer.used

      override def preStart(): Unit = buffer = BufferImpl(parallelism, inheritedAttributes)

      private def isCompleted = isClosed(in) && todo == 0

      def handle(msg: StreamCollectorCommand): Unit = {
        inFlight -= 1

        msg match {
          case EmitNext(elem: Out @unchecked) if elem != null =>
            if (isAvailable(out)) {
              if (!hasBeenPulled(in)) tryPull(in)
              push(out, elem)
              if (isCompleted) completeStage()
            } else buffer.enqueue(elem)
          case EmitNext(_) =>
            if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
          case TryPull =>
            if (!hasBeenPulled(in)) tryPull(in)
          case Complete =>
            completeStage()
          case Fail(ex) =>
            if (decider(ex) == Supervision.Stop) failStage(ex)
            else if (isCompleted) completeStage()
            else if (!hasBeenPulled(in)) tryPull(in)
        }
      }

      override def onPush(): Unit = {
        try {
          val elem = grab(in)
          transform(elem, this)
          inFlight += 1
        } catch {
          case NonFatal(ex) => if (decider(ex) == Supervision.Stop) failStage(ex)
        }
        if (todo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      override def onUpstreamFinish(): Unit = {
        if (todo == 0) completeStage()
      }

      override def onPull(): Unit = {
        if (!buffer.isEmpty) push(out, buffer.dequeue())

        val leftTodo = todo
        if (isClosed(in) && leftTodo == 0) completeStage()
        else if (leftTodo < parallelism && !hasBeenPulled(in)) tryPull(in)
      }

      setHandlers(in, out, this)
    }
}
