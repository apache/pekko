/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.routing

import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorRef
import pekko.actor.Props
import pekko.pattern.ask
import pekko.routing.ConsistentHashingRouter.ConsistentHashMapping
import pekko.routing.ConsistentHashingRouter.ConsistentHashable
import pekko.routing.ConsistentHashingRouter.ConsistentHashableEnvelope
import pekko.testkit._
import pekko.testkit.PekkoSpec

object ConsistentHashingRouterSpec {

  val config = """
    pekko.actor {
      # consistent hashing is serializing the hash key, unless it's bytes or string
      allow-java-serialization = on
    }
    pekko.actor.deployment {
      /router1 {
        router = consistent-hashing-pool
        nr-of-instances = 3
        virtual-nodes-factor = 17
      }
      /router2 {
        router = consistent-hashing-pool
        nr-of-instances = 5
      }
    }
    """

  class Echo extends Actor {
    def receive = {
      case x: ConsistentHashableEnvelope => sender() ! s"Unexpected envelope: $x"
      case _                             => sender() ! self
    }
  }

  final case class Msg(key: Any, data: String) extends ConsistentHashable {
    override def consistentHashKey = key
  }

  final case class MsgKey(name: String)

  final case class Msg2(key: Any, data: String)
}

class ConsistentHashingRouterSpec
    extends PekkoSpec(ConsistentHashingRouterSpec.config)
    with DefaultTimeout
    with ImplicitSender {
  import ConsistentHashingRouterSpec._
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  val router1 = system.actorOf(FromConfig.props(Props[Echo]()), "router1")

  "consistent hashing router" must {
    "create routees from configuration" in {
      val currentRoutees = Await.result(router1 ? GetRoutees, timeout.duration).asInstanceOf[Routees]
      currentRoutees.routees.size should ===(3)
    }

    "select destination based on consistentHashKey of the message" in {
      router1 ! Msg("a", "A")
      val destinationA = expectMsgType[ActorRef]
      router1 ! ConsistentHashableEnvelope(message = "AA", hashKey = "a")
      expectMsg(destinationA)

      router1 ! Msg(17, "B")
      val destinationB = expectMsgType[ActorRef]
      router1 ! ConsistentHashableEnvelope(message = "BB", hashKey = 17)
      expectMsg(destinationB)

      router1 ! Msg(MsgKey("c"), "C")
      val destinationC = expectMsgType[ActorRef]
      router1 ! ConsistentHashableEnvelope(message = "CC", hashKey = MsgKey("c"))
      expectMsg(destinationC)
    }

    "select destination with defined hashMapping" in {
      def hashMapping: ConsistentHashMapping = {
        case Msg2(key, _) => key
      }
      val router2 =
        system.actorOf(
          ConsistentHashingPool(nrOfInstances = 1, hashMapping = hashMapping).props(Props[Echo]()),
          "router2")

      router2 ! Msg2("a", "A")
      val destinationA = expectMsgType[ActorRef]
      router2 ! ConsistentHashableEnvelope(message = "AA", hashKey = "a")
      expectMsg(destinationA)

      router2 ! Msg2(17, "B")
      val destinationB = expectMsgType[ActorRef]
      router2 ! ConsistentHashableEnvelope(message = "BB", hashKey = 17)
      expectMsg(destinationB)

      router2 ! Msg2(MsgKey("c"), "C")
      val destinationC = expectMsgType[ActorRef]
      router2 ! ConsistentHashableEnvelope(message = "CC", hashKey = MsgKey("c"))
      expectMsg(destinationC)
    }
  }

}
