/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
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

  def copy(): OutboundEnvelope =
    (new ReusableOutboundEnvelope).init(_recipient, _message, _sender)

  def clear(): Unit = {
    _recipient = OptionVal.None
    _message = null
    _sender = OptionVal.None
  }

  def init(recipient: OptionVal[RemoteActorRef], message: AnyRef, sender: OptionVal[ActorRef]): OutboundEnvelope = {
    _recipient = recipient
    _message = message
    _sender = sender
    this
  }

  override def toString: String =
    s"OutboundEnvelope($recipient, $message, $sender)"
}
