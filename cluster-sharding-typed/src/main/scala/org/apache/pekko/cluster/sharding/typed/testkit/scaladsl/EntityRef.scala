/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.sharding.typed.testkit.scaladsl

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.cluster.sharding.typed.internal.testkit.TestEntityRefImpl
import pekko.cluster.sharding.typed.scaladsl.EntityRef
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey

/**
 * For testing purposes this `EntityRef` can be used in place of a real [[EntityRef]].
 * It forwards all messages to the `probe`.
 */
object TestEntityRef {
  def apply[M](typeKey: EntityTypeKey[M], entityId: String, probe: ActorRef[M]): EntityRef[M] =
    new TestEntityRefImpl[M](entityId, probe, typeKey)
}
