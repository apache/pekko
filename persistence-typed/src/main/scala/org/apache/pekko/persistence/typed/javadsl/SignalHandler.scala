/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.javadsl

import java.util.function.BiConsumer
import java.util.function.Consumer

import org.apache.pekko
import pekko.actor.typed.Signal
import pekko.annotation.InternalApi

object SignalHandler {
  private val Empty: SignalHandler[Any] = new SignalHandler[Any](PartialFunction.empty)

  def empty[State]: SignalHandler[State] = Empty.asInstanceOf[SignalHandler[State]]
}

final class SignalHandler[State](_handler: PartialFunction[(State, Signal), Unit]) {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def isEmpty: Boolean = _handler eq PartialFunction.empty

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def handler: PartialFunction[(State, Signal), Unit] = _handler
}

object SignalHandlerBuilder {
  def builder[State]: SignalHandlerBuilder[State] = new SignalHandlerBuilder
}

/**
 * Mutable builder for handling signals in [[EventSourcedBehavior]]
 *
 * Not for user instantiation, use [[EventSourcedBehavior.newSignalHandlerBuilder]] to get an instance.
 */
final class SignalHandlerBuilder[State] {

  private var handler: PartialFunction[(State, Signal), Unit] = PartialFunction.empty

  /**
   * If the behavior receives a signal of type `T`, `callback` is invoked with the signal instance as input.
   */
  def onSignal[T <: Signal](signalType: Class[T], callback: BiConsumer[State, T]): SignalHandlerBuilder[State] = {
    val newPF: PartialFunction[(State, Signal), Unit] = {
      case (state, t) if signalType.isInstance(t) =>
        callback.accept(state, t.asInstanceOf[T])
    }
    handler = newPF.orElse(handler)
    this
  }

  /**
   * If the behavior receives exactly the signal `signal`, `callback` is invoked.
   */
  def onSignal[T <: Signal](signal: T, callback: Consumer[State]): SignalHandlerBuilder[State] = {
    val newPF: PartialFunction[(State, Signal), Unit] = {
      case (state, `signal`) =>
        callback.accept(state)
    }
    handler = newPF.orElse(handler)
    this
  }

  def build: SignalHandler[State] = new SignalHandler(handler)

}
