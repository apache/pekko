/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.delivery

import java.util.UUID

import scala.concurrent.duration._
import scala.util.Try

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl._
import pekko.actor.typed.ActorRef
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

    "resume correctly after restart when all messages were confirmed" in {
      val producerId = "p-restart-clean"
      val producerProbe = createTestProbe[ProducerController.RequestNext[String]]()
      val consumerProbe = createTestProbe[ConsumerController.Delivery[String]]()

      // Phase 1: send one message and confirm it fully, then stop both controllers cleanly.
      val (pc1, cc1) = startProducerAndConsumer(producerId, producerProbe, consumerProbe)
      producerProbe.receiveMessage().sendNextTo ! "msg-1"
      val del1 = consumerProbe.receiveMessage()
      del1.confirmTo ! ConsumerController.Confirmed
      // Wait for the ProducerController to process the confirmation and issue a new RequestNext.
      // The seqNr is captured dynamically: for non-chunked messages it will be 2; for chunked
      // messages each byte is a separate seqNr, so it will be higher (e.g. 6 for a 5-byte string).
      // Note: StoreMessageConfirmed is intentionally write-behind (fire-and-forget, no reply), so
      // the confirmation may not yet be persisted to the journal when pc1 is stopped below.
      // If that happens, pc2 will re-deliver the earlier message — Phase 2 handles this case.
      val nextSeqNr = producerProbe.receiveMessage().currentSeqNr
      testKit.stop(pc1)
      producerProbe.expectTerminated(pc1)
      testKit.stop(cc1)
      consumerProbe.expectTerminated(cc1)

      // Phase 2: restart — must NOT crash, and must resume from the persisted sequence number.
      val (pc2, cc2) = startProducerAndConsumer(producerId, producerProbe, consumerProbe)

      // If StoreMessageConfirmed was not yet persisted when pc1 stopped, pc2 restarts with
      // non-empty unconfirmed and re-delivers the earlier message(s) BEFORE emitting RequestNext.
      // Causal ordering: cc2 enqueues a Delivery to consumerProbe in the same handler that
      // it enqueues a Request to pc2; pc2 only emits RequestNext after processing that Request.
      // Therefore, if any redelivery is coming, it arrives at consumerProbe before RequestNext
      // reaches producerProbe. Draining it first (short timeout) prevents producerProbe from
      // having to wait for the full redelivery round-trip before RequestNext arrives.
      // The timeout only applies to the no-redelivery (confirmation-persisted) case, where it
      // adds a small, bounded delay rather than an indefinite block.
      // 500 ms is generous for local actor messaging (typically < 10 ms) but short enough
      // that in the no-redelivery case the timeout does not slow down the test noticeably.
      Try(consumerProbe.receiveMessage(500.millis)).foreach { redelivery =>
        // A delivery before we have sent msg-2 can only be a redelivery of msg-1.
        redelivery.message should ===("msg-1")
        redelivery.confirmTo ! ConsumerController.Confirmed
      }

      // RequestNext now arrives quickly in both cases:
      //   - confirmed:     pc2 emitted it immediately from becomeActive (empty unconfirmed)
      //   - not confirmed: pc2 emitted it after cc2's initial Request (triggered by the redelivery)
      val req2 = producerProbe.receiveMessage()
      // The bug caused requestedSeqNr to be hardcoded to 1 regardless of the persisted state,
      // so currentSeqNr > requestedSeqNr and the controller crashed on the next message.
      req2.currentSeqNr should ===(nextSeqNr)
      req2.sendNextTo ! "msg-2"
      // For the chunked-message case, pc2 may have re-delivered only the first chunk via
      // ResendFirst before RequestNext was sent. Sending msg-2 triggers a Resend cycle for
      // the remaining chunks, so the assembled msg-1 may arrive at consumerProbe before msg-2.
      val firstDelivery = consumerProbe.receiveMessage()
      if (firstDelivery.message != "msg-2") {
        firstDelivery.message should ===("msg-1") // sanity-check: only msg-1 can be redelivered
        firstDelivery.confirmTo ! ConsumerController.Confirmed
        consumerProbe.receiveMessage().message should ===("msg-2")
      }
      testKit.stop(pc2)
      testKit.stop(cc2)
    }

  }

  // Helper to start a ProducerController (with EventSourcedProducerQueue) and a ConsumerController.
  // Returns both refs so callers can stop them independently.
  private def startProducerAndConsumer(
      producerId: String,
      producerProbe: TestProbe[ProducerController.RequestNext[String]],
      consumerProbe: TestProbe[ConsumerController.Delivery[String]]
  ): (ActorRef[ProducerController.Command[String]], ActorRef[ConsumerController.Command[String]]) = {

    val persistenceId = PersistenceId.ofUniqueId(producerId)
    val durableQueue = EventSourcedProducerQueue[String](persistenceId)

    val producerController = spawn(
      ProducerController[String](producerId, Some(durableQueue)))

    val consumerController = spawn(ConsumerController[String]())

    producerController ! ProducerController.RegisterConsumer(consumerController)
    producerController ! ProducerController.Start(producerProbe.ref)
    consumerController ! ConsumerController.Start(consumerProbe.ref)

    (producerController, consumerController)
  }

}

// same tests but with chunked messages
class ReliableDeliveryWithEventSourcedProducerQueueChunkedSpec
    extends ReliableDeliveryWithEventSourcedProducerQueueSpec(
      ConfigFactory.parseString("""
    pekko.reliable-delivery.producer-controller.chunk-large-messages = 1b
    """).withFallback(ReliableDeliveryWithEventSourcedProducerQueueSpec.conf))
