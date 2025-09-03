/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.annotation.nowarn
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Failure, Try }

import org.apache.pekko
import pekko.Done
import pekko.actor.{ Actor, ActorSystem, PoisonPill, Props }
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.stream.ActorMaterializerSpec.ActorWithMaterializer
import pekko.stream.impl.{ PhasedFusingActorMaterializer, StreamSupervisor }
import pekko.stream.scaladsl.{ Sink, Source }
import pekko.stream.testkit.{ StreamSpec, TestPublisher }
import pekko.testkit.{ ImplicitSender, TestProbe }
import pekko.testkit.TestKit

import com.typesafe.config.ConfigFactory

object IndirectMaterializerCreation extends ExtensionId[IndirectMaterializerCreation] with ExtensionIdProvider {
  def createExtension(system: ExtendedActorSystem): IndirectMaterializerCreation =
    new IndirectMaterializerCreation(system)

  def lookup: ExtensionId[IndirectMaterializerCreation] = this
}

@nowarn
class IndirectMaterializerCreation(ex: ExtendedActorSystem) extends Extension {
  // extension instantiation blocked on materializer (which has Await.result inside)
  implicit val mat: Materializer = ActorMaterializer()(ex)

  def futureThing(n: Int): Future[Int] = {
    Source.single(n).runWith(Sink.head)
  }

}

@nowarn
class ActorMaterializerSpec extends StreamSpec with ImplicitSender {

  "ActorMaterializer" must {

    "not suffer from deadlock" in {
      val n = 4
      implicit val deadlockSystem = ActorSystem(
        "ActorMaterializerSpec-deadlock",
        ConfigFactory.parseString(s"""
          pekko.actor.default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = $n
              parallelism-factor = 0.5
              parallelism-max = $n
            }
          }
          # undo stream testkit specific dispatcher and run "normally"
          pekko.actor.default-mailbox.mailbox-type = "org.apache.pekko.dispatch.UnboundedMailbox"
          pekko.stream.materializer.dispatcher = "pekko.actor.default-dispatcher"
          """))
      try {
        import deadlockSystem.dispatcher

        // tricky part here is that the concurrent access is to the extension
        // so the threads are indirectly blocked and not waiting for the Await.result(ask) directly.
        val result = Future.sequence((1 to (n + 1)).map(n =>
          Future {
            IndirectMaterializerCreation(deadlockSystem).mat
          }))

        // with starvation these fail
        result.futureValue.size should ===(n + 1)

      } finally {
        TestKit.shutdownActorSystem(deadlockSystem)
      }
    }

    "report shutdown status properly" in {
      val m = ActorMaterializer.create(system)

      m.isShutdown should ===(false)
      m.shutdown()
      m.isShutdown should ===(true)
    }

    "properly shut down actors associated with it" in {
      val m = ActorMaterializer.create(system)

      val f = Source.fromPublisher(TestPublisher.probe[Int]()(system)).runFold(0)(_ + _)(m)

      m.shutdown()

      an[AbruptTerminationException] should be thrownBy Await.result(f, 3.seconds)
    }

    "refuse materialization after shutdown" in {
      val m = ActorMaterializer.create(system)
      m.shutdown()
      (the[IllegalStateException] thrownBy {
        Source(1 to 5).runWith(Sink.ignore)(m)
      } should have).message("Trying to materialize stream after materializer has been shutdown")
    }

    "refuse materialization when shutdown while materializing" in {
      val m = ActorMaterializer.create(system)

      (the[IllegalStateException] thrownBy {
        Source(1 to 5)
          .mapMaterializedValue { _ =>
            // shutdown while materializing
            m.shutdown()
            Thread.sleep(100)
          }
          .runWith(Sink.ignore)(m)
      } should have).message("Materializer shutdown while materializing stream")
    }

    "shut down the supervisor actor it encapsulates" in {
      val m = ActorMaterializer.create(system).asInstanceOf[PhasedFusingActorMaterializer]

      Source.maybe[Any].to(Sink.ignore).run()(m)
      m.supervisor ! StreamSupervisor.GetChildren
      expectMsgType[StreamSupervisor.Children]
      m.shutdown()

      m.supervisor ! StreamSupervisor.GetChildren
      expectNoMessage(1.second)
    }

    "terminate if ActorContext it was created from terminates" in {
      val p = TestProbe()

      val a = system.actorOf(Props(new ActorWithMaterializer(p)).withDispatcher("pekko.test.stream-dispatcher"))

      p.expectMsg("hello")
      a ! PoisonPill
      val Failure(_) = p.expectMsgType[Try[Done]]: @unchecked
    }

    "report correctly if it has been shut down from the side" in {
      val sys = ActorSystem()
      val m = ActorMaterializer.create(sys)
      Await.result(sys.terminate(), Duration.Inf)
      m.isShutdown should ===(true)
    }
  }

}

object ActorMaterializerSpec {

  @nowarn("msg=deprecated")
  class ActorWithMaterializer(p: TestProbe) extends Actor {
    private val settings: ActorMaterializerSettings =
      ActorMaterializerSettings(context.system).withDispatcher("pekko.test.stream-dispatcher")
    implicit val mat: Materializer = ActorMaterializer(settings)(context)

    Source
      .repeat("hello")
      .take(1)
      .concat(Source.maybe)
      .map(p.ref ! _)
      .runWith(Sink.onComplete(signal => {
        p.ref ! signal
      }))

    def receive = Actor.emptyBehavior
  }
}
