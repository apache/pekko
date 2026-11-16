/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.cluster.sharding.typed.delivery

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.duration._

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ManualTime
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.Behavior
import pekko.actor.typed.BehaviorInterceptor
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.TypedActorContext
import pekko.actor.typed.delivery.ConsumerController
import pekko.actor.typed.delivery.internal.ProducerControllerImpl
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.sharding.typed.ShardingEnvelope
import pekko.cluster.sharding.typed.delivery.internal.ShardingProducerControllerImpl.InternalCommand

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

class ShardingProducerCleanupSpec
    extends ScalaTestWithActorTestKit(ConfigFactory.parseString("""
      cleanup-dispatcher {
        type = Dispatcher
        executor = "thread-pool-executor"
        thread-pool-executor.fixed-pool-size = 1
        throughput = 100
        throughput-deadline-time = 0ms
      }
      """).withFallback(ManualTime.config))
    with AnyWordSpecLike
    with LogCapturing {

  "ShardingProducerController" must {
    "resume delivery when a message arrives during idle cleanup" in {
      val entityId = "$entity/with spaces"
      val oldChildName = new AtomicReference[String]()
      val oldChildStillRegistered = createTestProbe[Boolean]()
      val region = createTestProbe[ShardingEnvelope[ConsumerController.SequencedMessage[String]]]()
      val producer = createTestProbe[ShardingProducerController.RequestNext[String]]()
      val confirmed = createTestProbe[Done]()
      val manualTime = ManualTime()
      val protocolMessages = createTestProbe[InternalCommand]()
      val captureProtocolMessages = new AtomicBoolean(false)
      val enqueueOnCleanup = new AtomicBoolean(false)
      val cleanupAfter = 10.millis
      val settings = ShardingProducerController.Settings(system).withCleanupUnusedAfter(cleanupAfter)
      val interceptor = new BehaviorInterceptor[InternalCommand, InternalCommand] {
        override def aroundReceive(
            ctx: TypedActorContext[InternalCommand],
            msg: InternalCommand,
            target: BehaviorInterceptor.ReceiveTarget[InternalCommand]): Behavior[InternalCommand] = {
          if (captureProtocolMessages.get()) protocolMessages.ref ! msg
          if (msg.getClass.getSimpleName == "CleanupUnused$" && enqueueOnCleanup.compareAndSet(true, false))
            ctx.asScala.self ! ShardingProducerController.MessageWithConfirmation(entityId, "second", confirmed.ref)
          msg match {
            case m: ShardingProducerController.MessageWithConfirmation[?] if m.message == "second" =>
              oldChildStillRegistered.ref ! ctx.asScala.child(oldChildName.get()).nonEmpty
            case _ =>
          }
          target(ctx, msg)
        }
      }
      // The parent processes cleanup and the queued message in one mailbox run. Its child uses
      // the same single-thread dispatcher, so it cannot terminate between those two messages.
      val controller = spawn(
        Behaviors.intercept(() => interceptor)(
          ShardingProducerController[String]("producer", region.ref, None, settings)
            .unsafeCast[InternalCommand]),
        DispatcherSelector.fromConfig("cleanup-dispatcher"))
      controller ! ShardingProducerController.Start(producer.ref)
      producer.receiveMessage()
      controller ! ShardingProducerController.MessageWithConfirmation(entityId, "first", confirmed.ref)
      val first = region.receiveMessage().message
      first.producerController.path.name should startWith("entity-%24entity%2Fwith+spaces-")
      oldChildName.set(first.producerController.path.name)
      captureProtocolMessages.set(true)
      first.producerController ! ProducerControllerImpl.Request(1L, 10L, supportResend = true, viaTimeout = false)
      // Capture the actual acknowledgement and demand messages without exposing private protocol classes.
      val obsoleteMessages = protocolMessages.receiveMessages(2)
      obsoleteMessages.map(_.getClass.getSimpleName) should contain theSameElementsAs Seq("Ack", "WrappedRequestNext")
      captureProtocolMessages.set(false)
      confirmed.expectMessage(Done)
      producer.receiveMessage().entitiesWithDemand should ===(Set(entityId))
      // A barrier also ensures that both captured messages have completed processing.
      controller ! ShardingProducerController.Start(producer.ref)
      producer.receiveMessage().entitiesWithDemand should ===(Set(entityId))
      // ManualTime prevents the periodic cleanup timer from running. Wait only for eligibility.
      val idleSince = System.nanoTime()
      producer.awaitAssert((System.nanoTime() - idleSince) should be >= cleanupAfter.toNanos)
      // All responses are processed; the only timer due now is the periodic cleanup.
      enqueueOnCleanup.set(true)
      manualTime.timePasses(cleanupAfter / 2)
      val second = region.receiveMessage().message
      oldChildStillRegistered.expectMessage(true)
      second.producerController.path.name should startWith("entity-%24entity%2Fwith+spaces-")
      second.producerController should not be first.producerController
      second.producerId should ===(first.producerId)
      second.first should ===(true)
      second.seqNr should ===(1L)
      second.message should ===("second")
      // Replay the real messages from the old incarnation after its replacement has sent a message.
      // Neither the old demand nor its acknowledgement may affect the replacement.
      obsoleteMessages.foreach(controller ! _)
      controller ! ShardingProducerController.Start(producer.ref)
      producer.receiveMessage().entitiesWithDemand should ===(Set.empty)
      confirmed.expectNoMessage(100.millis)
      second.producerController ! ProducerControllerImpl.Request(1L, 10L, supportResend = true, viaTimeout = false)
      confirmed.expectMessage(Done)
      testKit.stop(controller)
    }
  }
}
