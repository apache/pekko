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

package org.apache.pekko.stream

import org.apache.pekko
import pekko.actor.{ DeadLetterSuppression, NoSerializationVerificationNeeded }
import pekko.annotation.InternalApi

import scala.util.{ Failure, Success, Try }

object StreamCollectorOps {
  def emit[T](value: T)(implicit collector: StreamCollector[T]): Unit = collector.emit(value)
  def fail(throwable: Throwable)(implicit collector: StreamCollector[_]): Unit = collector.fail(throwable)
  def complete()(implicit collector: StreamCollector[_]): Unit = collector.complete()
  def handle[T](result: Try[T])(implicit collector: StreamCollector[T]): Unit = collector.handle(result)
  def handle[T](result: Either[Throwable, T])(implicit collector: StreamCollector[T]): Unit = collector.handle(result)
}

object StreamCollectorUnsafeOps {
  def emitSync[T](value: T)(implicit collector: StreamCollector[T]): Unit =
    collector.asInstanceOf[UnsafeStreamCollector[T]].emitSync(value)
  def failSync(throwable: Throwable)(implicit collector: StreamCollector[_]): Unit =
    collector.asInstanceOf[UnsafeStreamCollector[_]].failSync(throwable)
  def completeSync()(implicit collector: StreamCollector[_]): Unit =
    collector.asInstanceOf[UnsafeStreamCollector[_]].completeSync()
  def handleSync[T](result: Try[T])(implicit collector: StreamCollector[T]): Unit =
    collector.asInstanceOf[UnsafeStreamCollector[T]].handleSync(result)
  def handleSync[T](result: Either[Throwable, T])(implicit collector: StreamCollector[T]): Unit =
    collector.asInstanceOf[UnsafeStreamCollector[T]].handleSync(result)
}

object StreamCollector {
  sealed trait StreamCollectorCommand
      extends DeadLetterSuppression
      with NoSerializationVerificationNeeded

  case class EmitNext[T](value: T) extends StreamCollectorCommand

  case class Fail(throwable: Throwable) extends StreamCollectorCommand

  object Complete extends StreamCollectorCommand

  object TryPull extends StreamCollectorCommand
}

trait StreamCollector[T] {

  def emit(value: T): Unit

  def tryPull(): Unit = ()

  def fail(throwable: Throwable): Unit

  def complete(): Unit

  def handle(result: Try[T]): Unit = result match {
    case Success(value) => emit(value)
    case Failure(ex)    => fail(ex)
  }

  def handle(result: Either[Throwable, T]): Unit = result match {
    case Right(value) => emit(value)
    case Left(ex)     => fail(ex)
  }
}

@InternalApi
private[pekko] trait UnsafeStreamCollector[T] extends StreamCollector[T] {
  def emitSync(value: T): Unit
  def failSync(throwable: Throwable): Unit

  def completeSync(): Unit
  def handleSync(result: Try[T]): Unit = result match {
    case Success(value) => emitSync(value)
    case Failure(ex)    => failSync(ex)
  }
  def handleSync(result: Either[Throwable, T]): Unit = result match {
    case Right(value) => emitSync(value)
    case Left(ex)     => failSync(ex)
  }
}
