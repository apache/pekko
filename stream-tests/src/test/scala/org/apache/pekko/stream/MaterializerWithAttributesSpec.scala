/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Props
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.stage.GraphStageLogic
import pekko.stream.stage.GraphStageWithMaterializedValue
import pekko.stream.stage.OutHandler
import pekko.testkit.ImplicitSender
import pekko.testkit.PekkoSpec

object MaterializerWithAttributesSpec {

  /** Completes immediately and materializes the attributes it was materialized with */
  object AttributesSource extends GraphStageWithMaterializedValue[SourceShape[Nothing], Attributes] {
    val out: Outlet[Nothing] = Outlet[Nothing]("AttributesSource.out")
    override val shape: SourceShape[Nothing] = SourceShape(out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Attributes) = {
      val logic = new GraphStageLogic(shape) with OutHandler {
        override def preStart(): Unit = completeStage()
        override def onPull(): Unit = completeStage()

        setHandler(out, this)
      }
      (logic, inheritedAttributes)
    }
  }

  class StreamActor extends Actor {
    override def receive: Receive = {
      case "do it!" =>
        val materializerWithAttributes: Materializer = Materializer(context, Attributes.name("bar"))

        val firstAttributes =
          Source.fromGraph(AttributesSource).to(Sink.ignore).run()(materializerWithAttributes)
        val secondAttributes =
          Source.fromGraph(AttributesSource).to(Sink.ignore).run()(Materializer(context))

        sender() ! firstAttributes
        sender() ! secondAttributes

        context.stop(self)
    }
  }
}

class MaterializerWithAttributesSpec extends PekkoSpec with ImplicitSender {
  import MaterializerWithAttributesSpec._

  private def names(attributes: Attributes): List[String] =
    attributes.attributeList.collect { case Attributes.Name(n) => n }

  "A system level materializer with default attributes" should {
    "apply its attributes to streams it materializes" in {
      val materializerWithAttributes = Materializer(system, Attributes.name("foo"))

      val attributes = Source.fromGraph(AttributesSource).to(Sink.ignore).run()(materializerWithAttributes)
      names(attributes) should contain("foo")

      val otherAttributes = Source.fromGraph(AttributesSource).to(Sink.ignore).run()(Materializer(system))
      names(otherAttributes) shouldNot contain("foo")

      materializerWithAttributes.shutdown()
    }

    "let attributes on the stream override the materializer default attributes" in {
      val materializerWithAttributes = Materializer(system, Attributes.inputBuffer(1, 1))

      val attributes = Source.fromGraph(AttributesSource).to(Sink.ignore).run()(materializerWithAttributes)
      attributes.get[Attributes.InputBuffer] should ===(Some(Attributes.InputBuffer(1, 1)))

      val overridden = Source
        .fromGraph(AttributesSource)
        .withAttributes(Attributes.inputBuffer(2, 2))
        .to(Sink.ignore)
        .run()(materializerWithAttributes)
      overridden.get[Attributes.InputBuffer] should ===(Some(Attributes.InputBuffer(2, 2)))

      materializerWithAttributes.shutdown()
    }
  }

  "An actor context materializer with default attributes" should {
    "apply its attributes to streams it materializes" in {
      val streamActor = system.actorOf(Props(new StreamActor).withDispatcher("pekko.test.stream-dispatcher"))

      streamActor ! "do it!"

      val responses = receiveN(2, 3.seconds)
      responses.filterNot(_.isInstanceOf[Attributes]) shouldBe empty

      val allAttributes = responses.map(_.asInstanceOf[Attributes])
      names(allAttributes.head) should contain("bar")
      names(allAttributes.last) shouldNot contain("bar")
    }
  }
}
