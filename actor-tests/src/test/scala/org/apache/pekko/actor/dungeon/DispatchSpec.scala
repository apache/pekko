/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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
class DispatchSpec extends PekkoSpec("""
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
