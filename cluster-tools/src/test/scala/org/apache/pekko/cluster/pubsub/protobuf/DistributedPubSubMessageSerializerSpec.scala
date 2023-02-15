/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.pubsub.protobuf

import scala.collection.immutable.TreeMap

import org.apache.pekko
import pekko.actor.{ Address, ExtendedActorSystem }
import pekko.actor.Props
import pekko.cluster.pubsub.DistributedPubSubMediator._
import pekko.cluster.pubsub.DistributedPubSubMediator.Internal._
import pekko.testkit.PekkoSpec

class DistributedPubSubMessageSerializerSpec extends PekkoSpec {

  val serializer = new DistributedPubSubMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    val blob = serializer.toBinary(obj)
    val ref = serializer.fromBinary(blob, serializer.manifest(obj))
    ref should ===(obj)
  }

  " DistributedPubSubMessages" must {

    "be serializable" in {
      val address1 = Address("pekko", "system", "some.host.org", 4711)
      val address2 = Address("pekko", "system", "other.host.org", 4711)
      val address3 = Address("pekko", "system", "some.host.org", 4712)
      val u1 = system.actorOf(Props.empty, "u1")
      val u2 = system.actorOf(Props.empty, "u2")
      val u3 = system.actorOf(Props.empty, "u3")
      val u4 = system.actorOf(Props.empty, "u4")
      checkSerialization(Status(Map(address1 -> 3, address2 -> 17, address3 -> 5), isReplyToStatus = true))
      checkSerialization(
        Delta(List(
          Bucket(address1, 3, TreeMap("/user/u1" -> ValueHolder(2, Some(u1)), "/user/u2" -> ValueHolder(3, Some(u2)))),
          Bucket(address2, 17, TreeMap("/user/u3" -> ValueHolder(17, Some(u3)))),
          Bucket(address3, 5, TreeMap("/user/u4" -> ValueHolder(4, Some(u4)), "/user/u5" -> ValueHolder(5, None))))))
      checkSerialization(Send("/user/u3", "hello", localAffinity = true))
      checkSerialization(SendToAll("/user/u3", "hello", allButSelf = true))
      checkSerialization(Publish("mytopic", "hello"))
      checkSerialization(SendToOneSubscriber("hello"))
    }
  }
}
