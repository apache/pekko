/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem, ExtensionId, ExtensionIdProvider }
import pekko.actor.ClassicActorSystemProvider

/**
 * SerializationExtension is a Pekko Extension to interact with the Serialization
 * that is built into Akka
 */
object SerializationExtension extends ExtensionId[Serialization] with ExtensionIdProvider {
  override def get(system: ActorSystem): Serialization = super.get(system)
  override def get(system: ClassicActorSystemProvider): Serialization = super.get(system)
  override def lookup = SerializationExtension
  override def createExtension(system: ExtendedActorSystem): Serialization = new Serialization(system)
}
