/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import scala.concurrent.duration._

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.TestKitSettings
import pekko.actor.testkit.typed.scaladsl.{ ScalaTestWithActorTestKit, TestProbe }
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.typed.{ ActorRef, Behavior }
import pekko.actor.typed.internal.PoisonPill
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.cluster.typed.ClusterSingletonPoisonPillSpec.GetSelf

object ClusterSingletonPoisonPillSpec {

  final case class GetSelf(replyTo: ActorRef[ActorRef[Any]])
  val sneakyBehavior: Behavior[GetSelf] = Behaviors.receive {
    case (ctx, GetSelf(replyTo)) =>
      replyTo ! ctx.self.unsafeUpcast[Any]
      Behaviors.same
  }
}

class ClusterSingletonPoisonPillSpec
    extends ScalaTestWithActorTestKit(ClusterSingletonApiSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  implicit val testSettings: TestKitSettings = TestKitSettings(system)
  val clusterNode1 = Cluster(system)
  clusterNode1.manager ! Join(clusterNode1.selfMember.address)
  val classicSystem1 = system.toClassic
  "A typed cluster singleton" must {

    "support using PoisonPill to stop" in {
      val probe = TestProbe[ActorRef[Any]]()
      val singleton =
        ClusterSingleton(system).init(SingletonActor(ClusterSingletonPoisonPillSpec.sneakyBehavior, "sneaky"))
      singleton ! GetSelf(probe.ref)
      val singletonRef = probe.receiveMessage()
      singletonRef ! PoisonPill
      probe.expectTerminated(singletonRef, 1.second)
    }

  }

}
