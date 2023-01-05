/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io

import org.apache.pekko
import pekko.actor.Props
import pekko.io.UdpConnected.Connect

/**
 * INTERNAL API
 */
private[io] class UdpConnectedManager(udpConn: UdpConnectedExt)
    extends SelectionHandler.SelectorBasedManager(udpConn.settings, udpConn.settings.NrOfSelectors) {

  def receive = workerForCommandHandler {
    case c: Connect =>
      val commander = sender() // cache because we create a function that will run asynchly
      registry => Props(classOf[UdpConnection], udpConn, registry, commander, c)
  }

}
