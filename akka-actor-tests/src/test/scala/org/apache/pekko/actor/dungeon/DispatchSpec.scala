/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.dungeon

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Props
import pekko.testkit._

object DispatchSpec {
  class UnserializableMessageClass
  class EmptyActor extends Actor {
    override def receive = {
      case _: UnserializableMessageClass => // OK
    }
  }
}
class DispatchSpec extends AkkaSpec("""
  pekko.actor.serialize-messages = on
  pekko.actor.no-serialization-verification-needed-class-prefix = []
  """) with DefaultTimeout {
  import DispatchSpec._

  "The dispatcher" should {
    "log an appropriate message when pekko.actor.serialize-messages triggers a serialization error" in {
      val actor = system.actorOf(Props[EmptyActor]())
      EventFilter[Exception](pattern = ".*NoSerializationVerificationNeeded.*", occurrences = 1).intercept {
        actor ! new UnserializableMessageClass
      }
    }
  }
}
