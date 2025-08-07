/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.io

import java.io.OutputStream

import scala.concurrent.{ Future, Promise }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ Attributes, IOOperationIncompleteException, IOResult, Inlet, SinkShape }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStageLogic, GraphStageLogicWithLogging, GraphStageWithMaterializedValue, InHandler }
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class OutputStreamGraphStage(factory: () => OutputStream, autoFlush: Boolean)
    extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[IOResult]] {

  val in = Inlet[ByteString]("OutputStreamSink")

  override def shape: SinkShape[ByteString] = SinkShape(in)

  override protected def initialAttributes: Attributes = DefaultAttributes.outputStreamSink

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[IOResult]) = {
    val mat = Promise[IOResult]()
    val logic = new GraphStageLogicWithLogging(shape) with InHandler {
      var outputStream: OutputStream = _
      var bytesWritten: Long = 0L

      override protected def logSource: Class[_] = classOf[OutputStreamGraphStage]

      override def preStart(): Unit = {
        try {
          outputStream = factory()
          pull(in)
        } catch {
          case NonFatal(t) =>
            mat.tryFailure(new IOOperationIncompleteException(bytesWritten, t))
            failStage(t)
        }
      }

      override def onPush(): Unit = {
        val next = grab(in)
        try {
          outputStream.write(next.toArrayUnsafe())
          if (autoFlush) outputStream.flush()

          bytesWritten += next.size
          pull(in)
        } catch {
          case NonFatal(t) =>
            mat.tryFailure(new IOOperationIncompleteException(bytesWritten, t))
            failStage(t)
        }
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        mat.tryFailure(new IOOperationIncompleteException(bytesWritten, ex))
      }

      override def onUpstreamFinish(): Unit = {
        try {
          outputStream.flush()
        } catch {
          case NonFatal(t) =>
            mat.tryFailure(new IOOperationIncompleteException(bytesWritten, t))
        }
      }

      override def postStop(): Unit = {
        try {
          if (outputStream ne null) {
            outputStream.flush()
            outputStream.close()
          }
          mat.trySuccess(IOResult(bytesWritten))
        } catch {
          case NonFatal(t) =>
            mat.tryFailure(new IOOperationIncompleteException(bytesWritten, t))
        }
      }

      setHandler(in, this)
    }

    (logic, mat.future)

  }

}
