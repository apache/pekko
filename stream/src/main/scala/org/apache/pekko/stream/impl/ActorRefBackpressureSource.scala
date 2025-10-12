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

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._
import pekko.util.OptionVal

private object ActorRefBackpressureSource {
  private sealed trait ActorRefStage { def ref: ActorRef }
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class ActorRefBackpressureSource[T](
    ackTo: Option[ActorRef],
    ackMessage: Any,
    completionMatcher: PartialFunction[Any, CompletionStrategy],
    failureMatcher: PartialFunction[Any, Throwable])
    extends GraphStageWithMaterializedValue[SourceShape[T], ActorRef] {
  import ActorRefBackpressureSource._

  val out: Outlet[T] = Outlet[T]("actorRefSource.out")

  override val shape: SourceShape[T] = SourceShape.of(out)
  override def initialAttributes: Attributes = DefaultAttributes.actorRefWithBackpressureSource

  def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, ActorRef) =
    throw new IllegalStateException("Not supported")

  private[pekko] override def createLogicAndMaterializedValue(
      inheritedAttributes: Attributes,
      eagerMaterializer: Materializer): (GraphStageLogic, ActorRef) = {
    val stage: GraphStageLogic with OutHandler with StageLogging with ActorRefStage = new GraphStageLogic(shape)
      with OutHandler
      with StageLogging
      with ActorRefStage {
      override protected def logSource: Class[_] = classOf[ActorRefSource[_]]

      private var isCompleting: Boolean = false
      private var element: OptionVal[(ActorRef, T)] = OptionVal.none

      override protected def stageActorName: String =
        inheritedAttributes.get[Attributes.Name].map(_.n).getOrElse(super.stageActorName)

      val ref: ActorRef = getEagerStageActor(eagerMaterializer) {
        case (_, m) if failureMatcher.isDefinedAt(m) =>
          failStage(failureMatcher(m))
        case (_, m) if completionMatcher.isDefinedAt(m) =>
          completionMatcher(m) match {
            case CompletionStrategy.Draining =>
              isCompleting = true
              tryPush()
            case CompletionStrategy.Immediately =>
              completeStage()
          }
        case e: (ActorRef, T) @unchecked =>
          if (element.isDefined) {
            failStage(new IllegalStateException("Received new element before ack was signaled back"))
          } else {
            ackTo match {
              case Some(at) => element = OptionVal.Some((at, e._2))
              case None     => element = OptionVal.Some(e)
            }
            tryPush()
          }
        case _ => throw new RuntimeException() // compiler exhaustiveness check pleaser
      }.ref

      private def tryPush(): Unit = {
        if (isAvailable(out) && element.isDefined) {
          val (s, e) = element.get
          push(out, e)
          element = OptionVal.none
          s ! ackMessage
        }

        if (isCompleting && element.isEmpty) {
          completeStage()
        }
      }

      override def onPull(): Unit = tryPush()

      setHandler(out, this)
    }

    (stage, stage.ref)
  }
}
