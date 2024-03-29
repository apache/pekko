/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.Done
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts.parasitic
import pekko.stream._
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.SourceLocation
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage._
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class UnfoldResourceSourceAsync[T, S](
    create: () => Future[S],
    readData: (S) => Future[Option[T]],
    close: (S) => Future[Done])
    extends GraphStage[SourceShape[T]] {
  val out = Outlet[T]("UnfoldResourceSourceAsync.out")
  override val shape = SourceShape(out)
  override def initialAttributes: Attributes =
    DefaultAttributes.unfoldResourceSourceAsync and SourceLocation.forLambda(create)

  def createLogic(inheritedAttributes: Attributes) =
    new GraphStageLogic(shape) with OutHandler {
      private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
      private implicit def ec: ExecutionContext = materializer.executionContext
      private var maybeResource: OptionVal[S] = OptionVal.none

      private val createdCallback = getAsyncCallback[Try[S]] {
        case Success(resource) =>
          require(resource != null, "`create` method should not return a null resource.")
          maybeResource = OptionVal(resource)
          if (isAvailable(out)) onPull()
        case Failure(t) => failStage(t)
      }.invokeWithFeedback _

      private val errorHandler: PartialFunction[Throwable, Unit] = {
        case NonFatal(ex) =>
          decider(ex) match {
            case Supervision.Stop =>
              failStage(ex)
            case Supervision.Restart =>
              try {
                restartResource()
              } catch {
                case NonFatal(ex) => failStage(ex)
              }
            case Supervision.Resume => onPull()
          }
      }

      private val readCallback = getAsyncCallback[Try[Option[T]]](handle).invoke _

      private def handle(result: Try[Option[T]]): Unit = result match {
        case Success(data) =>
          data match {
            case Some(d) => push(out, d)
            case None    =>
              // end of resource reached, lets close it
              maybeResource match {
                case OptionVal.Some(resource) =>
                  close(resource).onComplete(getAsyncCallback[Try[Done]] {
                    case Success(Done) => completeStage()
                    case Failure(ex)   => failStage(ex)
                  }.invoke)(parasitic)
                  maybeResource = OptionVal.none

                case _ =>
                  // cannot happen, but for good measure
                  throw new IllegalStateException("Reached end of data but there is no open resource")
              }
          }
        case Failure(t) => errorHandler(t)
      }

      override def preStart(): Unit = createResource()

      override def onPull(): Unit = maybeResource match {
        case OptionVal.Some(resource) =>
          try {
            val future = readData(resource)
            future.value match {
              case Some(value) => handle(value)
              case None        => future.onComplete(readCallback)(parasitic)
            }
          } catch errorHandler

        case OptionVal.None =>
        // we got a pull but there is no open resource, we are either
        // currently creating/restarting then the read will be triggered when creating the
        // resource completes, or shutting down and then the pull does not matter anyway
      }

      override def postStop(): Unit = maybeResource match {
        case OptionVal.Some(resource) => close(resource)
        case _                        => // do nothing
      }

      private def restartResource(): Unit = maybeResource match {
        case OptionVal.Some(resource) =>
          // wait for the resource to close before restarting
          close(resource).onComplete(getAsyncCallback[Try[Done]] {
            case Success(Done) => createResource()
            case Failure(ex)   => failStage(ex)
          }.invoke)(parasitic)
          maybeResource = OptionVal.none

        case _ => createResource()
      }

      private def createResource(): Unit = {
        create().onComplete { resource =>
          createdCallback(resource).failed.foreach {
            case _: StreamDetachedException =>
              // stream stopped before created callback could be invoked, we need
              // to close the resource if it is was opened, to not leak it
              resource match {
                case Success(r) =>
                  close(r)
                case Failure(ex) =>
                  throw ex // failed to open but stream is stopped already
              }
            case _ => // we don't care here
          }
        }(parasitic)
      }

      setHandler(out, this)
    }

  override def toString = "UnfoldResourceSourceAsync"
}
