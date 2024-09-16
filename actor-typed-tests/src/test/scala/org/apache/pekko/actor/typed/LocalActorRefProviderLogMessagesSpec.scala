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

package org.apache.pekko.actor.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ ActorTestKit, LogCapturing, LoggingTestKit, ScalaTestWithActorTestKit }
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import org.scalatest.wordspec.AnyWordSpecLike

object LocalActorRefProviderLogMessagesSpec {
  val config = """
    pekko {
      loglevel = DEBUG # test verifies debug
      log-dead-letters = on
      actor {
        debug.unhandled = on
      }
    }
  """
}

class LocalActorRefProviderLogMessagesSpec
    extends ScalaTestWithActorTestKit(LocalActorRefProviderLogMessagesSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  "An LocalActorRefProvider" must {

    "logs on dedicated 'serialization' logger of unknown path" in {
      val provider = system.asInstanceOf[ActorSystemAdapter[_]].provider

      LoggingTestKit
        .debug("of unknown (invalid) path [dummy/path]")
        .withLoggerName("org.apache.pekko.actor.LocalActorRefProvider.Deserialization")
        .expect {
          provider.resolveActorRef("dummy/path")
        }
    }

    "logs on dedicated 'serialization' logger when path doesn't match existing actor" in {
      val provider = system.asInstanceOf[ActorSystemAdapter[_]].provider
      val invalidPath = provider.rootPath / "user" / "invalid"

      LoggingTestKit
        .debug("Resolve (deserialization) of path [user/invalid] doesn't match an active actor.")
        .withLoggerName("org.apache.pekko.actor.LocalActorRefProvider.Deserialization")
        .expect {
          provider.resolveActorRef(invalidPath)
        }
    }

    "logs on dedicated 'serialization' logger when of foreign path" in {

      val otherSystem = ActorTestKit("otherSystem").system.asInstanceOf[ActorSystemAdapter[_]]
      val invalidPath = otherSystem.provider.rootPath / "user" / "foo"

      val provider = system.asInstanceOf[ActorSystemAdapter[_]].provider
      try
        LoggingTestKit
          .debug("Resolve (deserialization) of foreign path [pekko://otherSystem/user/foo]")
          .withLoggerName("org.apache.pekko.actor.LocalActorRefProvider.Deserialization")
          .expect {
            provider.resolveActorRef(invalidPath)
          }
      finally
        ActorTestKit.shutdown(otherSystem)
    }
  }
}
