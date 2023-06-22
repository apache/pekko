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

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi

/**
 * Internal API
 *
 * To not create a new adapter for every `toTyped` call we create one instance and keep in an extension
 */
@InternalApi private[pekko] class AdapterExtension(sys: pekko.actor.ActorSystem) extends pekko.actor.Extension {
  val adapter = ActorSystemAdapter(sys)
}

/**
 * Internal API
 */
@InternalApi object AdapterExtension extends pekko.actor.ExtensionId[AdapterExtension] {
  def createExtension(sys: ExtendedActorSystem): AdapterExtension = new AdapterExtension(sys)
}
