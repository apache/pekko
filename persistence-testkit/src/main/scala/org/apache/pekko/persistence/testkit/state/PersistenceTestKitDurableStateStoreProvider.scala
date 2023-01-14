/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.state

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.persistence.state.DurableStateStoreProvider
import pekko.persistence.state.scaladsl.DurableStateStore
import pekko.persistence.testkit.state.scaladsl.PersistenceTestKitDurableStateStore
import pekko.persistence.state.javadsl.{ DurableStateStore => JDurableStateStore }
import pekko.persistence.testkit.state.javadsl.{
  PersistenceTestKitDurableStateStore => JPersistenceTestKitDurableStateStore
}

class PersistenceTestKitDurableStateStoreProvider(system: ExtendedActorSystem) extends DurableStateStoreProvider {
  private val _scaladslDurableStateStore = new PersistenceTestKitDurableStateStore[Any](system)
  override def scaladslDurableStateStore(): DurableStateStore[Any] = _scaladslDurableStateStore

  override def javadslDurableStateStore(): JDurableStateStore[AnyRef] =
    new JPersistenceTestKitDurableStateStore[AnyRef](
      _scaladslDurableStateStore.asInstanceOf[PersistenceTestKitDurableStateStore[AnyRef]])
}
