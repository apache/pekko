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
import pekko.actor.ActorSelection
import pekko.actor.ActorSelectionMessage
import pekko.actor.ExtendedActorSystem
import pekko.actor.LocalRef
import pekko.actor.PossiblyHarmful
import pekko.actor.RepointableRef
import pekko.dispatch.sysmsg.SystemMessage
import pekko.event.{ LogMarker, Logging }
import pekko.remote.RemoteActorRefProvider
import pekko.remote.RemoteRef
import pekko.util.OptionVal

/**
 * INTERNAL API
 */
private[remote] class MessageDispatcher(system: ExtendedActorSystem, provider: RemoteActorRefProvider) {

  private val remoteDaemon = provider.remoteDaemon
  private val log = Logging.withMarker(system, getClass.getName)
  private val debugLogEnabled: Boolean = log.isDebugEnabled

  def dispatch(inboundEnvelope: InboundEnvelope): Unit = {
    import Logging.messageClassName
    import provider.remoteSettings.Artery._

    val recipient = inboundEnvelope.recipient.get
    val message = inboundEnvelope.message
    val senderOption = inboundEnvelope.sender
    val originAddress = inboundEnvelope.association match {
      case OptionVal.Some(a) => OptionVal.Some(a.remoteAddress)
      case _                 => OptionVal.None
    }

    val sender: ActorRef = senderOption.getOrElse(system.deadLetters)

    recipient match {

      case `remoteDaemon` =>
        if (UntrustedMode) {
          if (debugLogEnabled)
            log.debug(LogMarker.Security, "dropping daemon message [{}] in untrusted mode", messageClassName(message))
        } else {
          if (LogReceive && debugLogEnabled)
            log.debug(
              "received daemon message [{}] from [{}]",
              message,
              senderOption.getOrElse(originAddress.getOrElse("")))
          remoteDaemon ! message
        }

      case l @ (_: LocalRef | _: RepointableRef) if l.isLocal =>
        if (LogReceive && debugLogEnabled)
          log.debug("received message [{}] to [{}] from [{}]", message, recipient, senderOption.getOrElse(""))
        message match {
          case sel: ActorSelectionMessage =>
            if (UntrustedMode && (!TrustedSelectionPaths.contains(sel.elements.mkString("/", "/", "")) ||
              sel.msg.isInstanceOf[PossiblyHarmful] || l != provider.rootGuardian)) {
              if (debugLogEnabled)
                log.debug(
                  LogMarker.Security,
                  "operating in UntrustedMode, dropping inbound actor selection to [{}], " +
                  "allow it by adding the path to 'pekko.remote.trusted-selection-paths' configuration",
                  sel.elements.mkString("/", "/", ""))
            } else
              // run the receive logic for ActorSelectionMessage here to make sure it is not stuck on busy user actor
              ActorSelection.deliverSelection(l, sender, sel)
          case msg: PossiblyHarmful if UntrustedMode =>
            if (debugLogEnabled)
              log.debug(
                LogMarker.Security,
                "operating in UntrustedMode, dropping inbound PossiblyHarmful message of type [{}] to [{}] from [{}]",
                messageClassName(msg),
                recipient,
                senderOption.getOrElse(originAddress.getOrElse("")))
          case msg: SystemMessage => l.sendSystemMessage(msg)
          case msg                => l.!(msg)(sender)
        }

      case r @ (_: RemoteRef | _: RepointableRef) if !r.isLocal && !UntrustedMode =>
        if (LogReceive && debugLogEnabled)
          log.debug(
            "received remote-destined message [{}] to [{}] from [{}]",
            message,
            recipient,
            senderOption.getOrElse(originAddress.getOrElse("")))
        // if it was originally addressed to us but is in fact remote from our point of view (i.e. remote-deployed)
        r.!(message)(sender)

      case r =>
        log.error(
          "dropping message [{}] for unknown recipient [{}] from [{}]",
          messageClassName(message),
          r,
          senderOption.getOrElse(originAddress.getOrElse("")))

    }
  }

}
