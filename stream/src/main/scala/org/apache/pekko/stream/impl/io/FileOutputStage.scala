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

import java.nio.channels.FileChannel
import java.nio.file.{ OpenOption, Path }

import scala.collection.immutable
import scala.concurrent.{ Future, Promise }
import scala.util.Success
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{
  AbruptStageTerminationException,
  Attributes,
  IOOperationIncompleteException,
  IOResult,
  Inlet,
  SinkShape
}
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler }
import pekko.util.ByteString
import pekko.util.ccompat.JavaConverters._

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class FileOutputStage(path: Path, startPosition: Long, openOptions: immutable.Set[OpenOption])
    extends GraphStageWithMaterializedValue[SinkShape[ByteString], Future[IOResult]] {

  val in: Inlet[ByteString] = Inlet("FileSink")
  override def shape: SinkShape[ByteString] = SinkShape(in)
  override def initialAttributes: Attributes = DefaultAttributes.fileSink

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[IOResult]) = {
    val mat = Promise[IOResult]()
    val logic = new GraphStageLogic(shape) with InHandler {
      private var chan: FileChannel = _
      private var bytesWritten: Long = 0

      override def preStart(): Unit =
        try {
          chan = FileChannel.open(path, openOptions.asJava)
          if (startPosition > 0) {
            chan.position(startPosition)
          }
          pull(in)
        } catch {
          case NonFatal(t) =>
            closeFile(Some(new IOOperationIncompleteException(bytesWritten, t)))
            failStage(t)
        }

      override def onPush(): Unit = {
        val next = grab(in)
        try {
          bytesWritten += chan.write(next.asByteBuffer)
          pull(in)
        } catch {
          case NonFatal(t) =>
            closeFile(Some(new IOOperationIncompleteException(bytesWritten, t)))
            failStage(t)
        }
      }

      override def onUpstreamFailure(t: Throwable): Unit = {
        closeFile(Some(new IOOperationIncompleteException(bytesWritten, t)))
        failStage(t)
      }

      override def onUpstreamFinish(): Unit = {
        closeFile(None)
        completeStage()
      }

      override def postStop(): Unit =
        if (!mat.isCompleted) {
          val failure = new AbruptStageTerminationException(this)
          closeFile(Some(failure))
          mat.tryFailure(failure)
        }

      private def closeFile(failed: Option[Throwable]): Unit =
        try {
          if (chan ne null) chan.close()
          failed match {
            case Some(t) => mat.tryFailure(t)
            case None    => mat.tryComplete(Success(IOResult(bytesWritten)))
          }
        } catch {
          case NonFatal(t) =>
            mat.tryFailure(failed.getOrElse(t))
        }

      setHandler(in, this)
    }
    (logic, mat.future)
  }
}
