/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package scala.docs.cluster

import language.postfixOps
import scala.concurrent.duration._
import org.apache.pekko.actor.Actor
import org.apache.pekko.actor.ActorRef
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.actor.Props
import org.apache.pekko.actor.RootActorPath
import org.apache.pekko.cluster.Cluster
import org.apache.pekko.cluster.ClusterEvent.CurrentClusterState
import org.apache.pekko.cluster.ClusterEvent.MemberUp
import org.apache.pekko.cluster.Member
import org.apache.pekko.cluster.MemberStatus
import com.typesafe.config.ConfigFactory

//#backend
class TransformationBackend extends Actor {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case TransformationJob(text) => sender() ! TransformationResult(text.toUpperCase)
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up).foreach(register)
    case MemberUp(m) => register(m)
  }

  def register(member: Member): Unit =
    if (member.hasRole("frontend"))
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
      BackendRegistration
}
//#backend

object TransformationBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory
      .parseString(s"pekko.remote.classic.netty.tcp.port=$port")
      .withFallback(ConfigFactory.parseString("pekko.cluster.roles = [backend]"))
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[TransformationBackend](), name = "backend")
  }
}
