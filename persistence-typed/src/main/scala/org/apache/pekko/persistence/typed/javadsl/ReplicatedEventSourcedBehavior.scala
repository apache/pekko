/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.javadsl

import java.util.Optional

import org.apache.pekko
import pekko.actor.typed.BackoffSupervisorStrategy
import pekko.actor.typed.Behavior
import pekko.actor.typed.TypedActorContext
import pekko.annotation.InternalApi
import pekko.persistence.typed.internal.ReplicationContextImpl

/**
 * Base class for replicated event sourced behaviors.
 */
abstract class ReplicatedEventSourcedBehavior[Command, Event, State](
    replicationContext: ReplicationContext,
    onPersistFailure: Optional[BackoffSupervisorStrategy])
    extends EventSourcedBehavior[Command, Event, State](replicationContext.persistenceId, onPersistFailure) {

  def this(replicationContext: ReplicationContext) = this(replicationContext, Optional.empty())

  /**
   * Override and return false to disable events being published to the system event stream as
   * [[pekko.persistence.typed.PublishedEvent]] after they have been persisted.
   */
  def withEventPublishing: Boolean = true

  protected def getReplicationContext(): ReplicationContext = replicationContext

  /**
   * INTERNAL API: DeferredBehavior init, not for user extension
   */
  @InternalApi override def apply(context: TypedActorContext[Command]): Behavior[Command] = {
    createEventSourcedBehavior()
      // context not user extendable so there should never be any other impls
      .withReplication(replicationContext.asInstanceOf[ReplicationContextImpl])
      .withEventPublishing(withEventPublishing)
  }
}
