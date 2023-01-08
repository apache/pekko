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

package docs.actor.typed

import org.apache.pekko.actor.typed.scaladsl.ActorContext

object DispatcherDocSpec {

  val context: ActorContext[Integer] = ???

  {
    // #defining-dispatcher-in-code
    import org.apache.pekko.actor.typed.DispatcherSelector
    val myActor =
      context.spawn(PrintActor(), "PrintActor", DispatcherSelector.fromConfig("PrintActor"))
    // #defining-dispatcher-in-code
  }

  {
    // #defining-fixed-pool-size-dispatcher
    import org.apache.pekko.actor.typed.DispatcherSelector
    val myActor =
      context.spawn(PrintActor(), "PrintActor", DispatcherSelector.fromConfig("blocking-io-dispatcher"))
    // #defining-fixed-pool-size-dispatcher
  }

  {
    // #lookup
    // for use with Futures, Scheduler, etc.
    import org.apache.pekko.actor.typed.DispatcherSelector
    implicit val executionContext = context.system.dispatchers.lookup(DispatcherSelector.fromConfig("my-dispatcher"))
    // #lookup
  }
}
