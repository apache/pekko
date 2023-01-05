/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.compress

import org.apache.pekko.actor._

object CompressionTestUtils {

  def minimalRef(name: String)(implicit system: ActorSystem): ActorRef =
    new MinimalActorRef {
      override def provider: ActorRefProvider = system.asInstanceOf[ActorSystemImpl].provider
      override def path: ActorPath = RootActorPath(provider.getDefaultAddress) / name
    }

}
