/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import OverflowStrategies._

import org.apache.pekko
import pekko.annotation.{ DoNotInherit, InternalApi }
import pekko.event.Logging
import pekko.event.Logging.LogLevel

/**
 * Represents a strategy that decides how to deal with a buffer of time based operator
 * that is full but is about to receive a new element.
 */
@DoNotInherit
sealed abstract class DelayOverflowStrategy extends Serializable {

  /** INTERNAL API */
  @InternalApi private[pekko] def isBackpressure: Boolean
}

final case class BufferOverflowException(msg: String) extends RuntimeException(msg)

/**
 * Represents a strategy that decides how to deal with a buffer that is full but is
 * about to receive a new element.
 */
@DoNotInherit
sealed abstract class OverflowStrategy extends DelayOverflowStrategy {

  /** INTERNAL API */
  @InternalApi private[pekko] def logLevel: LogLevel
  def withLogLevel(logLevel: Logging.LogLevel): OverflowStrategy
}

private[pekko] object OverflowStrategies {

  /**
   * INTERNAL API
   */
  private[pekko] case class DropHead(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): DropHead = DropHead(logLevel)
    private[pekko] override def isBackpressure: Boolean = false
  }

  /**
   * INTERNAL API
   */
  private[pekko] case class DropTail(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): DropTail = DropTail(logLevel)
    private[pekko] override def isBackpressure: Boolean = false
  }

  /**
   * INTERNAL API
   */
  private[pekko] case class DropBuffer(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): DropBuffer = DropBuffer(logLevel)
    private[pekko] override def isBackpressure: Boolean = false
  }

  /**
   * INTERNAL API
   */
  private[pekko] case class DropNew(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): DropNew = DropNew(logLevel)
    private[pekko] override def isBackpressure: Boolean = false
  }

  /**
   * INTERNAL API
   */
  private[pekko] case class Backpressure(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): Backpressure = Backpressure(logLevel)
    private[pekko] override def isBackpressure: Boolean = true
  }

  /**
   * INTERNAL API
   */
  private[pekko] case class Fail(logLevel: LogLevel) extends OverflowStrategy {
    override def withLogLevel(logLevel: LogLevel): Fail = Fail(logLevel)
    private[pekko] override def isBackpressure: Boolean = false
  }

  /**
   * INTERNAL API
   */
  private[pekko] case object EmitEarly extends DelayOverflowStrategy {
    private[pekko] override def isBackpressure: Boolean = true
  }
}

object OverflowStrategy {

  /**
   * If the buffer is full when a new element arrives, drops the oldest element from the buffer to make space for
   * the new element.
   */
  def dropHead: OverflowStrategy = DropHead(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops the youngest element from the buffer to make space for
   * the new element.
   */
  def dropTail: OverflowStrategy = DropTail(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops all the buffered elements to make space for the new element.
   */
  def dropBuffer: OverflowStrategy = DropBuffer(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops the new element.
   *
   * @deprecated Use {@link pekko.stream.javadsl.Source#queue(int,org.apache.pekko.stream.OverflowStrategy)} instead
   */
  @deprecated("Use Source.queue instead", "2.6.11")
  @Deprecated
  def dropNew: OverflowStrategy = DropNew(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element is available this strategy backpressures the upstream publisher until
   * space becomes available in the buffer.
   */
  def backpressure: OverflowStrategy = Backpressure(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element is available this strategy completes the stream with failure.
   */
  def fail: OverflowStrategy = Fail(Logging.ErrorLevel)
}

object DelayOverflowStrategy {

  /**
   * If the buffer is full when a new element is available this strategy send next element downstream without waiting
   * Will backpressure if downstream is not ready.
   */
  def emitEarly: DelayOverflowStrategy = EmitEarly

  /**
   * If the buffer is full when a new element arrives, drops the oldest element from the buffer to make space for
   * the new element.
   */
  def dropHead: DelayOverflowStrategy = DropHead(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops the youngest element from the buffer to make space for
   * the new element.
   */
  def dropTail: DelayOverflowStrategy = DropTail(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops all the buffered elements to make space for the new element.
   */
  def dropBuffer: DelayOverflowStrategy = DropBuffer(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element arrives, drops the new element.
   */
  def dropNew: DelayOverflowStrategy = DropNew(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element is available this strategy backpressures the upstream publisher until
   * space becomes available in the buffer.
   */
  def backpressure: DelayOverflowStrategy = Backpressure(Logging.DebugLevel)

  /**
   * If the buffer is full when a new element is available this strategy completes the stream with failure.
   */
  def fail: DelayOverflowStrategy = Fail(Logging.ErrorLevel)
}
