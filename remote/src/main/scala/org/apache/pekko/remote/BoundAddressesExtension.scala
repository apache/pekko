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

package org.apache.pekko.remote

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.Address
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.remote.artery.ArteryTransport

/**
 * Extension provides access to bound addresses.
 */
object BoundAddressesExtension extends ExtensionId[BoundAddressesExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): BoundAddressesExtension = super.get(system)
  override def get(system: ClassicActorSystemProvider): BoundAddressesExtension = super.get(system)

  override def lookup = BoundAddressesExtension

  override def createExtension(system: ExtendedActorSystem): BoundAddressesExtension =
    new BoundAddressesExtension(system)
}

class BoundAddressesExtension(val system: ExtendedActorSystem) extends Extension {

  /**
   * Returns a mapping from a protocol to a set of bound addresses.
   */
  def boundAddresses: Map[String, Set[Address]] = system.provider.asInstanceOf[RemoteActorRefProvider].transport match {
    case artery: ArteryTransport => Map(ArteryTransport.ProtocolName -> Set(artery.bindAddress.address))
    case remoting: Remoting      => remoting.boundAddresses
    case other                   => throw new IllegalStateException(s"Unexpected transport type: ${other.getClass}")
  }
}
