/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.actor.typed

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

// #print-actor
object PrintActor {
  def apply(): Behavior[Integer] =
    Behaviors.receiveMessage { i =>
      println(s"PrintActor: $i")
      Behaviors.same
    }
}
// #print-actor
