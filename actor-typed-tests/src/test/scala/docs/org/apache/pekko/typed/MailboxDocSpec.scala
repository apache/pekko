/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.Behavior
import pekko.actor.typed.MailboxSelector
import pekko.actor.typed.scaladsl.Behaviors
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

class MailboxDocSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.load("mailbox-config-sample.conf"))
    with AnyWordSpecLike
    with LogCapturing {

  "Specifying mailbox through props" must {
    "work" in {
      val probe = createTestProbe[Done]()
      val childBehavior: Behavior[String] = Behaviors.empty
      val parent: Behavior[Unit] = Behaviors.setup { context =>
        // #select-mailbox
        context.spawn(childBehavior, "bounded-mailbox-child", MailboxSelector.bounded(100))

        val props = MailboxSelector.fromConfig("my-app.my-special-mailbox")
        context.spawn(childBehavior, "from-config-mailbox-child", props)
        // #select-mailbox

        probe.ref ! Done
        Behaviors.stopped
      }
      spawn(parent)

      probe.receiveMessage()
    }
  }

}
