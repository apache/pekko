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

package org.apache.pekko.remote.artery.compress

import org.apache.pekko.actor._

object CompressionTestUtils {

  def minimalRef(name: String)(implicit system: ActorSystem): ActorRef =
    new MinimalActorRef {
      override def provider: ActorRefProvider = system.asInstanceOf[ActorSystemImpl].provider
      override def path: ActorPath = RootActorPath(provider.getDefaultAddress) / name
    }

}
