/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit

import java.util.UUID

import org.apache.pekko
import pekko.actor.{ ActorRef, ActorSystem }
import pekko.persistence._
import pekko.testkit.TestKitBase

import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

trait CommonUtils extends AnyWordSpecLike with TestKitBase {

  protected def randomPid() = UUID.randomUUID().toString

  import scala.jdk.CollectionConverters._

  def initSystemWithEnabledPlugin(name: String, serializeMessages: Boolean, serializeSnapshots: Boolean) =
    ActorSystem(
      s"persistence-testkit-$name",
      PersistenceTestKitSnapshotPlugin.config
        .withFallback(PersistenceTestKitPlugin.config)
        .withFallback(
          ConfigFactory.parseMap(
            Map(
              // testing serialization of the events when persisting in the storage
              // using default java serializers for convenience
              "pekko.actor.allow-java-serialization" -> true,
              "pekko.persistence.testkit.events.serialize" -> serializeMessages,
              "pekko.persistence.testkit.snapshots.serialize" -> serializeSnapshots).asJava))
        .withFallback(ConfigFactory.parseString("pekko.loggers = [\"org.apache.pekko.testkit.TestEventListener\"]"))
        .withFallback(ConfigFactory.defaultApplication()))

}

case class NewSnapshot(state: Any)
case object DeleteAllMessages
case class DeleteSomeSnapshot(seqNum: Long)
case class DeleteSomeSnapshotByCriteria(crit: SnapshotSelectionCriteria)
case object AskMessageSeqNum
case object AskSnapshotSeqNum
case class DeleteSomeMessages(upToSeqNum: Long)

class C

case class B(i: Int)

class A(pid: String, notifyOnStateChange: Option[ActorRef]) extends PersistentActor {

  import scala.collection.immutable

  var recovered = immutable.List.empty[Any]
  var snapshotState = 0

  override def receiveRecover = {
    case SnapshotOffer(_, snapshot: Int) =>
      snapshotState = snapshot
    case RecoveryCompleted =>
      notifyOnStateChange.foreach(_ ! Tuple2(recovered, snapshotState))
    case s => recovered :+= s
  }

  override def receiveCommand = {
    case AskMessageSeqNum =>
      notifyOnStateChange.foreach(_ ! lastSequenceNr)
    case AskSnapshotSeqNum =>
      notifyOnStateChange.foreach(_ ! snapshotSequenceNr)
    case d @ DeleteMessagesFailure(_, _) =>
      notifyOnStateChange.foreach(_ ! d)
    case d @ DeleteMessagesSuccess(_) =>
      notifyOnStateChange.foreach(_ ! d)
    case s: SnapshotProtocol.Response =>
      notifyOnStateChange.foreach(_ ! s)
    case DeleteAllMessages =>
      deleteMessages(lastSequenceNr)
    case DeleteSomeSnapshot(sn) =>
      deleteSnapshot(sn)
    case DeleteSomeSnapshotByCriteria(crit) =>
      deleteSnapshots(crit)
    case DeleteSomeMessages(sn) =>
      deleteMessages(sn)
    case NewSnapshot(state: Int) =>
      snapshotState = state: Int
      saveSnapshot(state)
    case NewSnapshot(other) =>
      saveSnapshot(other)
    case s =>
      persist(s) { _ =>
        sender() ! s
      }
  }

  override def persistenceId = pid
}

sealed trait TestCommand
case class Cmd(data: String) extends TestCommand
case object Passivate extends TestCommand
case class Evt(data: String)
case class EmptyState()
case class NonEmptyState(data: String)
case object Recovered
case object Stopped
