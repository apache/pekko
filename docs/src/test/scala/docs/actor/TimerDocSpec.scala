/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.actor

object TimerDocSpec {
  // #timers
  import scala.concurrent.duration._

  import org.apache.pekko
  import pekko.actor.Actor
  import pekko.actor.Timers

  object MyActor {
    private case object TickKey
    private case object FirstTick
    private case object Tick
  }

  class MyActor extends Actor with Timers {
    import MyActor._
    timers.startSingleTimer(TickKey, FirstTick, 500.millis)

    def receive = {
      case FirstTick =>
        // do something useful here
        timers.startTimerWithFixedDelay(TickKey, Tick, 1.second)
      case Tick =>
      // do something useful here
    }
  }
  // #timers
}
