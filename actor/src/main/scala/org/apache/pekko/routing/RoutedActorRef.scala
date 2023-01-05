/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.routing

import scala.annotation.nowarn

import org.apache.pekko
import pekko.ConfigurationException
import pekko.actor.ActorPath
import pekko.actor.ActorSystemImpl
import pekko.actor.Cell
import pekko.actor.InternalActorRef
import pekko.actor.Props
import pekko.actor.RepointableActorRef
import pekko.actor.UnstartedCell
import pekko.dispatch.BalancingDispatcher
import pekko.dispatch.MailboxType
import pekko.dispatch.MessageDispatcher

/**
 * INTERNAL API
 *
 * A RoutedActorRef is an ActorRef that has a set of connected ActorRef and it uses a Router to
 * send a message to one (or more) of these actors.
 */
@nowarn("msg=deprecated")
private[pekko] class RoutedActorRef(
    _system: ActorSystemImpl,
    _routerProps: Props,
    _routerDispatcher: MessageDispatcher,
    _routerMailbox: MailboxType,
    _routeeProps: Props,
    _supervisor: InternalActorRef,
    _path: ActorPath)
    extends RepointableActorRef(_system, _routerProps, _routerDispatcher, _routerMailbox, _supervisor, _path) {

  // verify that a BalancingDispatcher is not used with a Router
  if (_routerProps.routerConfig != NoRouter && _routerDispatcher.isInstanceOf[BalancingDispatcher]) {
    throw new ConfigurationException(
      "Configuration for " + this +
      " is invalid - you can not use a 'BalancingDispatcher' as a Router's dispatcher, you can however use it for the routees.")
  } else _routerProps.routerConfig.verifyConfig(_path)

  override def newCell(old: UnstartedCell): Cell = {
    val cell = props.routerConfig match {
      case pool: Pool if pool.resizer.isDefined =>
        new ResizablePoolCell(system, this, props, dispatcher, _routeeProps, supervisor, pool)
      case _ =>
        new RoutedActorCell(system, this, props, dispatcher, _routeeProps, supervisor)
    }
    cell.init(sendSupervise = false, mailboxType)
  }

}
