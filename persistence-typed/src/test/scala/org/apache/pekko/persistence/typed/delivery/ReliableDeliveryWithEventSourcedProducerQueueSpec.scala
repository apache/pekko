/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.delivery

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.delivery.ConsumerController
import pekko.actor.typed.delivery.ProducerController
import pekko.persistence.typed.PersistenceId

object ReliableDeliveryWithEventSourcedProducerQueueSpec {
  def conf: Config =
    ConfigFactory.parseString(s"""
    pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
    pekko.persistence.journal.inmem.test-serialization = on
    pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
    pekko.persistence.snapshot-store.local.dir = "target/ProducerControllerWithEventSourcedProducerQueueSpec-${UUID
        .randomUUID()
        .toString}"
    pekko.reliable-delivery.consumer-controller.flow-control-window = 20
    """)
}

class ReliableDeliveryWithEventSourcedProducerQueueSpec(config: Config)
    extends ScalaTestWithActorTestKit(config)
    with AnyWordSpecLike
    with LogCapturing {

  def this() = this(ReliableDeliveryWithEventSourcedProducerQueueSpec.conf)

  "ReliableDelivery with EventSourcedProducerQueue" must {

    "deliver messages after full producer and consumer restart" in {
      val producerId = "p1"
      val producerProbe = createTestProbe[ProducerController.RequestNext[String]]()

      val producerController = spawn(
        ProducerController[String](
          producerId,
          Some(EventSourcedProducerQueue[String](PersistenceId.ofUniqueId(producerId)))))
      producerController ! ProducerController.Start(producerProbe.ref)

      val consumerController = spawn(ConsumerController[String]())
      val consumerProbe = createTestProbe[ConsumerController.Delivery[String]]()
      consumerController ! ConsumerController.Start(consumerProbe.ref)
      consumerController ! ConsumerController.RegisterToProducerController(producerController)

      producerProbe.receiveMessage().sendNextTo ! "a"
      producerProbe.receiveMessage().sendNextTo ! "b"
      producerProbe.receiveMessage().sendNextTo ! "c"
      producerProbe.receiveMessage()

      consumerProbe.receiveMessage().message should ===("a")

      system.log.info("Stopping [{}]", producerController)
      testKit.stop(producerController)
      producerProbe.expectTerminated(producerController)
      testKit.stop(consumerController)
      consumerProbe.expectTerminated(consumerController)

      val producerController2 = spawn(
        ProducerController[String](
          producerId,
          Some(EventSourcedProducerQueue[String](PersistenceId.ofUniqueId(producerId)))))
      producerController2 ! ProducerController.Start(producerProbe.ref)

      val consumerController2 = spawn(ConsumerController[String]())
      consumerController2 ! ConsumerController.Start(consumerProbe.ref)
      consumerController2 ! ConsumerController.RegisterToProducerController(producerController2)

      val delivery1 = consumerProbe.receiveMessage()
      delivery1.message should ===("a")
      delivery1.confirmTo ! ConsumerController.Confirmed

      val delivery2 = consumerProbe.receiveMessage()
      delivery2.message should ===("b")
      delivery2.confirmTo ! ConsumerController.Confirmed

      val delivery3 = consumerProbe.receiveMessage()
      delivery3.message should ===("c")
      delivery3.confirmTo ! ConsumerController.Confirmed

      val requestNext4 = producerProbe.receiveMessage()
      requestNext4.currentSeqNr should ===(4)
      requestNext4.sendNextTo ! "d"

      val delivery4 = consumerProbe.receiveMessage()
      delivery4.message should ===("d")
      delivery4.confirmTo ! ConsumerController.Confirmed

      testKit.stop(producerController2)
      testKit.stop(consumerController2)
    }

    "deliver messages after producer restart, keeping same ConsumerController" in {
      val producerId = "p2"
      val producerProbe = createTestProbe[ProducerController.RequestNext[String]]()

      val producerController = spawn(
        ProducerController[String](
          producerId,
          Some(EventSourcedProducerQueue[String](PersistenceId.ofUniqueId(producerId)))))
      producerController ! ProducerController.Start(producerProbe.ref)

      val consumerController = spawn(ConsumerController[String]())
      val consumerProbe = createTestProbe[ConsumerController.Delivery[String]]()
      consumerController ! ConsumerController.Start(consumerProbe.ref)
      consumerController ! ConsumerController.RegisterToProducerController(producerController)

      producerProbe.receiveMessage().sendNextTo ! "a"
      producerProbe.receiveMessage().sendNextTo ! "b"
      producerProbe.receiveMessage().sendNextTo ! "c"
      producerProbe.receiveMessage()

      val delivery1 = consumerProbe.receiveMessage()
      delivery1.message should ===("a")

      system.log.info("Stopping [{}]", producerController)
      testKit.stop(producerController)

      consumerProbe.expectTerminated(producerController)

      val producerController2 = spawn(
        ProducerController[String](
          producerId,
          Some(EventSourcedProducerQueue[String](PersistenceId.ofUniqueId(producerId)))))
      producerController2 ! ProducerController.Start(producerProbe.ref)
      consumerController ! ConsumerController.RegisterToProducerController(producerController2)

      delivery1.confirmTo ! ConsumerController.Confirmed

      val requestNext4 = producerProbe.receiveMessage()
      requestNext4.currentSeqNr should ===(4)
      requestNext4.sendNextTo ! "d"

      // TODO Should we try harder to deduplicate first?
      val redelivery1 = consumerProbe.receiveMessage()
      redelivery1.message should ===("a")
      redelivery1.confirmTo ! ConsumerController.Confirmed

      producerProbe.receiveMessage().sendNextTo ! "e"

      val redelivery2 = consumerProbe.receiveMessage()
      redelivery2.message should ===("b")
      redelivery2.confirmTo ! ConsumerController.Confirmed

      val redelivery3 = consumerProbe.receiveMessage()
      redelivery3.message should ===("c")
      redelivery3.confirmTo ! ConsumerController.Confirmed

      val delivery4 = consumerProbe.receiveMessage()
      delivery4.message should ===("d")
      delivery4.confirmTo ! ConsumerController.Confirmed

      val delivery5 = consumerProbe.receiveMessage()
      delivery5.message should ===("e")
      delivery5.confirmTo ! ConsumerController.Confirmed

      testKit.stop(producerController2)
      testKit.stop(consumerController)
    }

  }

}

// same tests but with chunked messages
class ReliableDeliveryWithEventSourcedProducerQueueChunkedSpec
    extends ReliableDeliveryWithEventSourcedProducerQueueSpec(
      ConfigFactory.parseString("""
    pekko.reliable-delivery.producer-controller.chunk-large-messages = 1b
    """).withFallback(ReliableDeliveryWithEventSourcedProducerQueueSpec.conf))
