/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io.compression

import scala.annotation.tailrec
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success }

import org.apache.pekko
import pekko.stream.Materializer
import pekko.stream.scaladsl.Source
import pekko.util.ByteString
import pekko.util.ccompat._

// a few useful helpers copied over from pekko-http
@ccompatUsedUntil213
object CompressionTestingTools {
  implicit class AddFutureAwaitResult[T](val future: Future[T]) extends AnyVal {

    /** "Safe" Await.result that doesn't throw away half of the stacktrace */
    def awaitResult(atMost: Duration): T = {
      Await.ready(future, atMost)
      future.value.get match {
        case Success(t) => t
        case Failure(ex) =>
          throw new RuntimeException(
            "Trying to await result of failed Future, see the cause for the original problem.",
            ex)
      }
    }
  }
  implicit class EnhancedByteStringTraversableOnce(val byteStrings: IterableOnce[ByteString]) extends AnyVal {
    def join: ByteString = byteStrings.iterator.foldLeft(ByteString.empty)(_ ++ _)
  }
  implicit class EnhancedByteStringSource[Mat](val byteStringStream: Source[ByteString, Mat]) extends AnyVal {
    def join(implicit materializer: Materializer): Future[ByteString] =
      byteStringStream.runFold(ByteString.empty)(_ ++ _)
    def utf8String(implicit materializer: Materializer, ec: ExecutionContext): Future[String] =
      join.map(_.utf8String)
  }

  implicit class EnhancedThrowable(val throwable: Throwable) extends AnyVal {
    def ultimateCause: Throwable = {
      @tailrec def rec(ex: Throwable): Throwable =
        if (ex.getCause == null) ex
        else rec(ex.getCause)

      rec(throwable)
    }
  }
}
