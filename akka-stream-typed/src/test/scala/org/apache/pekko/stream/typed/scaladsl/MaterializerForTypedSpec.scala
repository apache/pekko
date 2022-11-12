/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.typed.scaladsl

import scala.concurrent.Future
import scala.util.Success
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.scaladsl.Behaviors
import pekko.stream.AbruptStageTerminationException
import pekko.stream.Materializer
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source

class MaterializerForTypedSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  "Materialization in typed" should {

    "use system materializer by default" in {
      val it: Future[String] = Source.single("hello").runWith(Sink.head)
      it.futureValue should ===("hello")
    }

    "allow for custom instances for special cases" in {
      val customMaterializer = Materializer(system)
      val it: Future[String] = Source.single("hello").runWith(Sink.head)(customMaterializer)

      it.futureValue should ===("hello")
    }

    "allow for actor context bound instances" in {
      val probe = testKit.createTestProbe[Any]()
      val actor = testKit.spawn(Behaviors.setup[String] { context =>
        val materializerForActor = Materializer(context)

        Behaviors.receiveMessagePartial[String] {
          case "run" =>
            val f = Source.single("hello").runWith(Sink.head)(materializerForActor)
            f.onComplete(probe.ref ! _)(system.executionContext)
            Behaviors.same
        }
      })
      actor ! "run"
      probe.expectMessage(Success("hello"))

    }

    "should kill streams with bound actor context" in {
      var doneF: Future[Done] = null
      val behavior =
        Behaviors.setup[String] { ctx =>
          implicit val mat: Materializer = Materializer(ctx)
          doneF = Source.repeat("hello").runWith(Sink.ignore)

          Behaviors.receiveMessage[String](_ => Behaviors.stopped)
        }

      val actorRef = spawn(behavior)

      actorRef ! "kill"
      eventually(doneF.failed.futureValue shouldBe an[AbruptStageTerminationException])
    }
  }
}
