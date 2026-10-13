/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.actor.NoSerializationVerificationNeeded
import pekko.remote.RemoteActorRef
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
private[remote] object OutboundEnvelope {
  def apply(recipient: OptionVal[RemoteActorRef], message: AnyRef, sender: OptionVal[ActorRef]): OutboundEnvelope = {
    val env = new ReusableOutboundEnvelope
    env.init(recipient, message, sender)
  }

}

/**
 * INTERNAL API
 */
private[remote] trait OutboundEnvelope extends NoSerializationVerificationNeeded {
  def recipient: OptionVal[RemoteActorRef]
  def message: AnyRef
  def sender: OptionVal[ActorRef]

  def withMessage(message: AnyRef): OutboundEnvelope

  def copy(): OutboundEnvelope
}

/**
 * INTERNAL API
 */
private[remote] object ReusableOutboundEnvelope {
  def createObjectPool(capacity: Int) =
    new ObjectPool[ReusableOutboundEnvelope](
      capacity,
      create = () => new ReusableOutboundEnvelope,
      clear = outEnvelope => outEnvelope.clear())
}

/**
 * INTERNAL API
 */
private[remote] final class ReusableOutboundEnvelope extends OutboundEnvelope {
  private var _recipient: OptionVal[RemoteActorRef] = OptionVal.None
  private var _message: AnyRef = null
  private var _sender: OptionVal[ActorRef] = OptionVal.None

  override def recipient: OptionVal[RemoteActorRef] = _recipient
  override def message: AnyRef = _message
  override def sender: OptionVal[ActorRef] = _sender

  override def withMessage(message: AnyRef): OutboundEnvelope = {
    _message = message
    this
  }

  /**
   * <p>
   *   Opentelemetry Java Instrumentation relies on this method so avoid changing it. The agent carries the
   *   captured context over to the copy.
   *   See https://github.com/apache/pekko/issues/3472
   * </p>
   */
  // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/19823
  @noinline // Not inlined so that the agent can match the method in the bytecode
  def copy(): OutboundEnvelope =
    (new ReusableOutboundEnvelope).init(_recipient, _message, _sender)

  /**
   * <p>
   *   Opentelemetry Java Instrumentation relies on this method so avoid changing it. Envelopes are pooled,
   *   so the agent clears the previous context here to stop it leaking into the next use.
   *   See https://github.com/apache/pekko/issues/3472
   * </p>
   */
  // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/19823
  @noinline // Not inlined so that the agent can match the method in the bytecode
  def clear(): Unit = {
    _recipient = OptionVal.None
    _message = null
    _sender = OptionVal.None
  }

  /**
   * <p>
   *   Opentelemetry Java Instrumentation relies on this method so avoid changing it. The agent captures the
   *   sender's context here, and the arity is part of the match.
   *   See https://github.com/apache/pekko/issues/3472
   * </p>
   */
  // see https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/19823
  @noinline // Not inlined so that the agent can match the method in the bytecode
  def init(recipient: OptionVal[RemoteActorRef], message: AnyRef, sender: OptionVal[ActorRef]): OutboundEnvelope = {
    _recipient = recipient
    _message = message
    _sender = sender
    this
  }

  override def toString: String =
    s"OutboundEnvelope($recipient, $message, $sender)"
}
