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

package org.apache.pekko.stream.impl

import scala.util.control.NonFatal

import org.apache.pekko

import pekko.actor.{ ActorRef, FunctionRef, Kill, PoisonPill }
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.OverflowStrategies._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._
import pekko.util.OptionVal

private object ActorRefSource {
  private sealed trait ActorRefStage { def ref: ActorRef }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class ActorRefSource[T](
    maxBuffer: Int,
    overflowStrategy: OverflowStrategy,
    completionMatcher: PartialFunction[Any, CompletionStrategy],
    failureMatcher: PartialFunction[Any, Throwable])
    extends GraphStageWithMaterializedValue[SourceShape[T], ActorRef] {
  import ActorRefSource._

  val out: Outlet[T] = Outlet[T]("actorRefSource.out")

  override val shape: SourceShape[T] = SourceShape.of(out)
  override def initialAttributes: Attributes = DefaultAttributes.actorRefSource

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, ActorRef) =
    throw new IllegalStateException("Not supported")

  private[pekko] override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes,
      eagerMaterializer: Materializer): (GraphStageLogic, ActorRef) = {
    val stage: GraphStageLogic with StageLogging with ActorRefStage = new GraphStageLogic(shape)
      with OutHandler
      with StageLogging
      with ActorRefStage {
      override protected def logSource: Class[?] = classOf[ActorRefSource[?]]

      private val buffer: OptionVal[Buffer[T]] =
        if (maxBuffer != 0)
          OptionVal(Buffer(maxBuffer, inheritedAttributes))
        else {
          OptionVal.None // for backwards compatibility with old actor publisher based implementation
        }
      private var isCompleting: Boolean = false

      private val name = inheritedAttributes.nameOrDefault(getClass.toString)

      private val cell = resolveSupervisorCell(eagerMaterializer)

      private val callback = getAsyncCallback[Any](onMessage)

      private val functionRef: FunctionRef = {
        val actorName = inheritedAttributes.nameForActorRef(super.stageActorName)
        val systemLog = eagerMaterializer.system.log
        cell.addFunctionRef(
          (_, msg) =>
            msg match {
              case PoisonPill | Kill =>
                systemLog.warning(
                  "{} message sent to ActorRefSource({}) will be ignored, since it is not a real Actor. " +
                  "Use a custom message type to communicate with it instead.",
                  msg,
                  functionRef.path)
              case _ => callback.invoke(msg)
            },
          actorName)
      }

      override val ref: ActorRef = functionRef

      override def postStop(): Unit = {
        try cell.removeFunctionRef(functionRef)
        catch { case NonFatal(_) => () }
        super.postStop()
      }

      private def onMessage(msg: Any): Unit = {
        if (failureMatcher.isDefinedAt(msg)) {
          failStage(failureMatcher(msg))
        } else if (completionMatcher.isDefinedAt(msg)) {
          completionMatcher(msg) match {
            case CompletionStrategy.Draining =>
              isCompleting = true
              tryPush()
            case CompletionStrategy.Immediately =>
              completeStage()
          }
        } else {
          msg match {
            case m: T @unchecked =>
              buffer match {
                case OptionVal.Some(buf) =>
                  if (isCompleting) {
                    log.warning(
                      "Dropping element because Status.Success received already, only draining already buffered elements: [{}] (pending: [{}] in stream [{}])",
                      m,
                      buf.used,
                      name)
                  } else if (buf.isEmpty && isAvailable(out)) {
                    // Direct-push fast path: safe to skip tryPush() because isCompleting
                    // is checked above, and completion is driven by the completionMatcher branch.
                    push(out, m)
                  } else if (!buf.isFull) {
                    buf.enqueue(m)
                    tryPush()
                  } else
                    overflowStrategy match {
                      case s: DropHead =>
                        log.log(
                          s.logLevel,
                          "Dropping the head element because buffer is full and overflowStrategy is: [DropHead] in stream [{}]",
                          name)
                        buf.dropHead()
                        buf.enqueue(m)
                        tryPush()
                      case s: DropTail =>
                        log.log(
                          s.logLevel,
                          "Dropping the tail element because buffer is full and overflowStrategy is: [DropTail] in stream [{}]",
                          name)
                        buf.dropTail()
                        buf.enqueue(m)
                        tryPush()
                      case s: DropBuffer =>
                        log.log(
                          s.logLevel,
                          "Dropping all the buffered elements because buffer is full and overflowStrategy is: [DropBuffer] in stream [{}]",
                          name)
                        buf.clear()
                        buf.enqueue(m)
                        tryPush()
                      case s: Fail =>
                        log.log(
                          s.logLevel,
                          "Failing because buffer is full and overflowStrategy is: [Fail] in stream [{}]",
                          name)
                        val bufferOverflowException =
                          BufferOverflowException(s"Buffer overflow (max capacity was: $maxBuffer)!")
                        failStage(bufferOverflowException)
                      case _: Backpressure =>
                        failStage(new IllegalStateException("Backpressure is not supported"))
                    }
                case _ =>
                  if (isCompleting) {
                    log.warning(
                      "Dropping element because Status.Success received already: [{}] in stream [{}]",
                      m,
                      name)
                  } else if (isAvailable(out)) {
                    push(out, m)
                  } else {
                    log.debug(
                      "Dropping element because there is no downstream demand and no buffer: [{}] in stream [{}]",
                      m,
                      name)
                  }
              }
            case unexpected =>
              log.debug("Dropping unexpected non-data message [{}] in stream [{}]", unexpected, name)
          }
        }
      }

      private def tryPush(): Unit = {
        if (isAvailable(out) && buffer.isDefined && buffer.get.nonEmpty) {
          val msg = buffer.get.dequeue()
          push(out, msg)
        }

        if (isCompleting && (buffer.isEmpty || buffer.get.isEmpty)) {
          completeStage()
        }
      }

      override def onPull(): Unit = tryPush()

      setHandler(out, this)
    }

    (stage, stage.ref)
  }
}
