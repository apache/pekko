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

package docs.routing

import org.apache.pekko.testkit.PekkoSpec
import org.apache.pekko.testkit.ImplicitSender
import org.apache.pekko.routing.FromConfig
import org.apache.pekko.actor.ActorRef

object ConsistentHashingRouterDocSpec {

  // #cache-actor
  import org.apache.pekko
  import pekko.actor.Actor
  import pekko.routing.ConsistentHashingRouter.ConsistentHashable

  class Cache extends Actor {
    var cache = Map.empty[String, String]

    def receive = {
      case Entry(key, value) => cache += (key -> value)
      case Get(key)          => sender() ! cache.get(key)
      case Evict(key)        => cache -= key
    }
  }

  final case class Evict(key: String)

  final case class Get(key: String) extends ConsistentHashable {
    override def consistentHashKey: Any = key
  }

  final case class Entry(key: String, value: String)
  // #cache-actor

}

class ConsistentHashingRouterDocSpec extends PekkoSpec with ImplicitSender {

  import ConsistentHashingRouterDocSpec._

  "demonstrate usage of ConsistentHashableRouter" in {

    def context = system

    // #consistent-hashing-router
    import org.apache.pekko
    import pekko.actor.Props
    import pekko.routing.ConsistentHashingPool
    import pekko.routing.ConsistentHashingRouter.ConsistentHashMapping
    import pekko.routing.ConsistentHashingRouter.ConsistentHashableEnvelope

    def hashMapping: ConsistentHashMapping = {
      case Evict(key) => key
    }

    val cache: ActorRef =
      context.actorOf(ConsistentHashingPool(10, hashMapping = hashMapping).props(Props[Cache]()), name = "cache")

    cache ! ConsistentHashableEnvelope(message = Entry("hello", "HELLO"), hashKey = "hello")
    cache ! ConsistentHashableEnvelope(message = Entry("hi", "HI"), hashKey = "hi")

    cache ! Get("hello")
    expectMsg(Some("HELLO"))

    cache ! Get("hi")
    expectMsg(Some("HI"))

    cache ! Evict("hi")
    cache ! Get("hi")
    expectMsg(None)

    // #consistent-hashing-router

  }

}
