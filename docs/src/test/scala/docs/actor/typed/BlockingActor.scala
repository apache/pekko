/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.actor.typed

// #blocking-in-actor
import org.apache.pekko
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.Behaviors

object BlockingActor {
  def apply(): Behavior[Int] =
    Behaviors.receiveMessage { i =>
      // DO NOT DO THIS HERE: this is an example of incorrect code,
      // better alternatives are described further on.

      // block for 5 seconds, representing blocking I/O, etc
      Thread.sleep(5000)
      println(s"Blocking operation finished: $i")
      Behaviors.same
    }
}
// #blocking-in-actor
