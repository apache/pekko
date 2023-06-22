/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.scaladsl.adapter

import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.actor.typed.SupervisorStrategy
import pekko.actor.typed.scaladsl.Behaviors

/**
 * Wrap [[pekko.actor.typed.Behavior]] in a classic [[pekko.actor.Props]], i.e. when
 * spawning a typed child actor from a classic parent actor.
 * This is normally not needed because you can use the extension methods
 * `spawn` and `spawnAnonymous` on a classic `ActorContext`, but it's needed
 * when using typed actors with an existing library/tool that provides an API that
 * takes a classic [[pekko.actor.Props]] parameter. Cluster Sharding is an
 * example of that.
 */
object PropsAdapter {
  def apply[T](behavior: => Behavior[T], deploy: Props = Props.empty): pekko.actor.Props =
    pekko.actor.typed.internal.adapter.PropsAdapter(
      () => Behaviors.supervise(behavior).onFailure(SupervisorStrategy.stop),
      deploy,
      rethrowTypedFailure = false)
}
