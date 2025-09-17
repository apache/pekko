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

package org.apache.pekko.stream.impl.io

import java.io.{ IOException, OutputStream }
import java.util.concurrent.{ Semaphore, TimeUnit }

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.stream.Attributes.InputBuffer
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.io.OutputStreamSourceStage._
import pekko.stream.stage._
import pekko.stream.{ Attributes, Outlet, SourceShape }
import pekko.util.ByteString

private[stream] object OutputStreamSourceStage {
  sealed trait AdapterToStageMessage
  case class Send(data: ByteString) extends AdapterToStageMessage
  case object Close extends AdapterToStageMessage
}

final private[stream] class OutputStreamSourceStage(writeTimeout: FiniteDuration)
    extends GraphStageWithMaterializedValue[SourceShape[ByteString], OutputStream] {
  val out = Outlet[ByteString]("OutputStreamSource.out")
  override def initialAttributes = DefaultAttributes.outputStreamSource
  override val shape: SourceShape[ByteString] = SourceShape.of(out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, OutputStream) = {
    val maxBuffer = inheritedAttributes.get[InputBuffer](InputBuffer(16, 16)).max

    require(maxBuffer > 0, "Buffer size must be greater than 0")

    // Semaphore counting the number of elements we are ready to accept.
    // Initially we are ready to accept 'maxBuffer' elements, which will be buffered
    // by 'emit' if there is no demand yet.
    // Semaphore permits are taken out of the pool when inserting data into the
    // OutputStream, and new permits are released when downstream signals demand.
    val semaphore = new Semaphore(maxBuffer, /* fair =*/ true)

    final class OutputStreamSourceLogic extends GraphStageLogic(shape) {

      val upstreamCallback: AsyncCallback[AdapterToStageMessage] =
        getAsyncCallback(onAsyncMessage)

      private def onAsyncMessage(event: AdapterToStageMessage): Unit = {
        event match {
          case Send(data) =>
            emit(out, data, () => semaphore.release())
          case Close =>
            completeStage()
        }
      }

      setHandler(out, GraphStageLogic.EagerTerminateOutput)
    }

    val logic = new OutputStreamSourceLogic
    (logic, new OutputStreamAdapter(semaphore, logic.upstreamCallback, writeTimeout))
  }
}

private[pekko] class OutputStreamAdapter(
    unfulfilledDemand: Semaphore,
    sendToStage: AsyncCallback[AdapterToStageMessage],
    writeTimeout: FiniteDuration)
    extends OutputStream {

  @scala.throws(classOf[IOException])
  private[this] def sendData(data: ByteString): Unit = {
    if (!unfulfilledDemand.tryAcquire(writeTimeout.toMillis, TimeUnit.MILLISECONDS)) {
      throw new IOException("Timed out trying to write data to stream")
    }

    try {
      Await.result(sendToStage.invokeWithFeedback(Send(data)), writeTimeout)
    } catch {
      case NonFatal(e) => throw new IOException(e)
    }
  }

  @scala.throws(classOf[IOException])
  override def write(b: Int): Unit = {
    sendData(ByteString(b))
  }

  @scala.throws(classOf[IOException])
  override def write(b: Array[Byte], off: Int, len: Int): Unit = {
    if (b.nonEmpty) {
      sendData(ByteString.fromArray(b, off, len))
    }
  }

  @scala.throws(classOf[IOException])
  override def flush(): Unit =
    // Flushing does nothing: at best we could guarantee that our own buffer
    // is empty, but that doesn't mean the element has been accepted downstream,
    // so there is little value in that.
    ()

  @scala.throws(classOf[IOException])
  override def close(): Unit = {
    try {
      Await.result(sendToStage.invokeWithFeedback(Close), writeTimeout)
    } catch {
      case NonFatal(e) => throw new IOException(e)
    }
  }
}
