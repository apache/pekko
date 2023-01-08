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

package org.apache.pekko.actor

import org.apache.pekko
import pekko.annotation.DoNotInherit
import pekko.annotation.InternalApi

/**
 * Glue API introduced to allow minimal user effort integration between classic and typed for example for streams.
 *
 * Not for user extension.
 */
@DoNotInherit
trait ClassicActorSystemProvider {

  /**
   * Allows access to the classic `org.apache.pekko.actor.ActorSystem` even for `org.apache.pekko.actor.typed.ActorSystem[_]`s.
   */
  def classicSystem: ActorSystem
}

/**
 * Glue API introduced to allow minimal user effort integration between classic and typed for example for streams.
 *
 * Not for user extension.
 */
@DoNotInherit
trait ClassicActorContextProvider {

  /** INTERNAL API */
  @InternalApi
  private[pekko] def classicActorContext: ActorContext
}
