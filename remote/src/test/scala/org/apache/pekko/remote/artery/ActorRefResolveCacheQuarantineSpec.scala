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

package org.apache.pekko.remote.artery

import org.apache.pekko
import pekko.remote.RARP
import pekko.testkit.DeadLettersFilter
import pekko.testkit.ImplicitSender
import pekko.testkit.TestActors
import pekko.testkit.TestEvent.Mute
import pekko.testkit.TestDuration
import pekko.pattern.ask
import pekko.util.Timeout

/**
 * Reproducer of issue #29828
 */
class ActorRefResolveCacheQuarantineSpec
    extends ArteryMultiNodeSpec("""
      pekko.remote.artery.advanced.remove-quarantined-association-after = 2 seconds
      """)
    with ImplicitSender {
  import RemoteFailureSpec._

  private implicit val timeout: Timeout = testKitSettings.SingleExpectDefaultTimeout.dilated

  system.eventStream.publish(Mute(DeadLettersFilter(classOf[Ping])(occurrences = Int.MaxValue)))

  "ActorRefResolveCache" should {

    "not use cached quarantined association" in {
      system.actorOf(TestActors.echoActorProps, name = "echo")

      val clientSystem1 = newRemoteSystem()
      val remoteSelection1 = clientSystem1.actorSelection(rootActorPath(system) / "user" / "echo")

      // PromiseActorRef (temp) doesn't include a uid in the ActorRef
      val reply1 = (remoteSelection1 ? "hello-1").futureValue
      reply1 shouldBe "hello-1"

      shutdown(clientSystem1)

      // wait for it to be removed fully, remove-quarantined-association-after
      Thread.sleep(4000)

      val port1 = RARP(clientSystem1).provider.getDefaultAddress.getPort().get
      val clientSystem2 =
        newRemoteSystem(
          name = Some(clientSystem1.name),
          extraConfig = Some(s"pekko.remote.artery.canonical.port = $port1"))
      val remoteSelection2 = clientSystem2.actorSelection(rootActorPath(system) / "user" / "echo")

      val reply2 = (remoteSelection2 ? "hello-2").futureValue
      reply2 shouldBe "hello-2"
    }

  }
}
