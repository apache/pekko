/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.actor.typed.SpawnProtocol.Spawn
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.{ ActorRef, Behavior, DispatcherSelector, Props, SpawnProtocol }
import pekko.dispatch.Dispatcher
import DispatchersDocSpec._
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike
import scala.concurrent.Future
import pekko.actor.testkit.typed.scaladsl.LogCapturing

object DispatchersDocSpec {

  val config = ConfigFactory.parseString("""
       //#config
      your-dispatcher {
        type = Dispatcher
        executor = "thread-pool-executor"
        thread-pool-executor {
          fixed-pool-size = 32
        }
        throughput = 1
      }
       //#config
    """.stripMargin)

  case class WhichDispatcher(replyTo: ActorRef[Dispatcher])

  val giveMeYourDispatcher = Behaviors.receive[WhichDispatcher] { (context, message) =>
    message.replyTo ! context.executionContext.asInstanceOf[Dispatcher]
    Behaviors.same
  }

  val yourBehavior: Behavior[String] = Behaviors.same

  val example = Behaviors.receive[Any] { (context, _) =>
    // #spawn-dispatcher
    import org.apache.pekko.actor.typed.DispatcherSelector

    context.spawn(yourBehavior, "DefaultDispatcher")
    context.spawn(yourBehavior, "ExplicitDefaultDispatcher", DispatcherSelector.default())
    context.spawn(yourBehavior, "BlockingDispatcher", DispatcherSelector.blocking())
    context.spawn(yourBehavior, "ParentDispatcher", DispatcherSelector.sameAsParent())
    context.spawn(yourBehavior, "DispatcherFromConfig", DispatcherSelector.fromConfig("your-dispatcher"))
    // #spawn-dispatcher

    Behaviors.same
  }

}

class DispatchersDocSpec
    extends ScalaTestWithActorTestKit(DispatchersDocSpec.config)
    with AnyWordSpecLike
    with LogCapturing {

  "Actor Dispatchers" should {
    "support default and blocking dispatcher" in {
      val probe = TestProbe[Dispatcher]()
      val actor: ActorRef[SpawnProtocol.Command] = spawn(SpawnProtocol())

      val withDefault: Future[ActorRef[WhichDispatcher]] =
        actor.ask(Spawn(giveMeYourDispatcher, "default", Props.empty, _))
      withDefault.futureValue ! WhichDispatcher(probe.ref)
      probe.receiveMessage().id shouldEqual "akka.actor.default-dispatcher"

      val withBlocking: Future[ActorRef[WhichDispatcher]] =
        actor.ask(Spawn(giveMeYourDispatcher, "default", DispatcherSelector.blocking(), _))
      withBlocking.futureValue ! WhichDispatcher(probe.ref)
      probe.receiveMessage().id shouldEqual "akka.actor.default-blocking-io-dispatcher"

      val withCustom: Future[ActorRef[WhichDispatcher]] =
        actor.ask(Spawn(giveMeYourDispatcher, "default", DispatcherSelector.fromConfig("your-dispatcher"), _))

      withCustom.futureValue ! WhichDispatcher(probe.ref)
      probe.receiveMessage().id shouldEqual "your-dispatcher"
    }
  }
}
