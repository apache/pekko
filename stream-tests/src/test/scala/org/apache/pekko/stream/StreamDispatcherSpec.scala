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

package org.apache.pekko.stream

import org.apache.pekko
import pekko.dispatch.Dispatchers
import pekko.stream.testkit.StreamSpec

class StreamDispatcherSpec extends StreamSpec {

  "The default blocking io dispatcher for streams" must {

    "be the same as the default blocking io dispatcher for actors" in {
      val streamIoDispatcher = system.dispatchers.lookup(ActorAttributes.IODispatcher.dispatcher)
      val actorIoDispatcher = system.dispatchers.lookup(Dispatchers.DefaultBlockingDispatcherId)

      streamIoDispatcher shouldBe theSameInstanceAs(actorIoDispatcher)
    }

  }

  "The deprecated default stream io dispatcher" must {
    "be the same as the default blocking io dispatcher for actors" in {
      // in case it is still used
      val streamIoDispatcher = system.dispatchers.lookup("pekko.stream.default-blocking-io-dispatcher")
      val actorIoDispatcher = system.dispatchers.lookup(Dispatchers.DefaultBlockingDispatcherId)

      streamIoDispatcher shouldBe theSameInstanceAs(actorIoDispatcher)
    }

  }
}
