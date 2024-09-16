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

package docs.org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
import pekko.pattern.StatusReply
import pekko.persistence.typed.PersistenceId
import pekko.persistence.typed.state.scaladsl.Effect
import pekko.persistence.typed.state.scaladsl.DurableStateBehavior
import pekko.serialization.jackson.CborSerializable

/**
 * This bank account example illustrates:
 * - Option[State] that is starting with None as the initial state
 * - command handlers in the state classes
 * - replies of various types, using withEnforcedReplies
 */
object AccountExampleWithOptionDurableState {

  // #account-entity
  object AccountEntity {
    // Command
    sealed trait Command extends CborSerializable
    final case class CreateAccount(replyTo: ActorRef[StatusReply[Done]]) extends Command
    final case class Deposit(amount: BigDecimal, replyTo: ActorRef[StatusReply[Done]]) extends Command
    final case class Withdraw(amount: BigDecimal, replyTo: ActorRef[StatusReply[Done]]) extends Command
    final case class GetBalance(replyTo: ActorRef[CurrentBalance]) extends Command
    final case class CloseAccount(replyTo: ActorRef[StatusReply[Done]]) extends Command

    // Reply
    final case class CurrentBalance(balance: BigDecimal) extends CborSerializable

    val Zero = BigDecimal(0)

    // type alias to reduce boilerplate
    type ReplyEffect = pekko.persistence.typed.state.scaladsl.ReplyEffect[Option[Account]]

    // State
    sealed trait Account extends CborSerializable {
      def applyCommand(cmd: Command): ReplyEffect
    }
    case class OpenedAccount(balance: BigDecimal) extends Account {
      require(balance >= Zero, "Account balance can't be negative")

      override def applyCommand(cmd: Command): ReplyEffect =
        cmd match {
          case Deposit(amount, replyTo) =>
            Effect.persist(Some(copy(balance = balance + amount))).thenReply(replyTo)(_ => StatusReply.Ack)

          case Withdraw(amount, replyTo) =>
            if (canWithdraw(amount))
              Effect.persist(Some(copy(balance = balance - amount))).thenReply(replyTo)(_ => StatusReply.Ack)
            else
              Effect.reply(replyTo)(StatusReply.Error(s"Insufficient balance $balance to be able to withdraw $amount"))

          case GetBalance(replyTo) =>
            Effect.reply(replyTo)(CurrentBalance(balance))

          case CloseAccount(replyTo) =>
            if (balance == Zero)
              Effect.persist(Some(ClosedAccount)).thenReply(replyTo)(_ => StatusReply.Ack)
            else
              Effect.reply(replyTo)(StatusReply.Error("Can't close account with non-zero balance"))

          case CreateAccount(replyTo) =>
            Effect.reply(replyTo)(StatusReply.Error("Account is already created"))

        }

      def canWithdraw(amount: BigDecimal): Boolean =
        balance - amount >= Zero
    }

    case object ClosedAccount extends Account {
      override def applyCommand(cmd: Command): ReplyEffect =
        cmd match {
          case c: Deposit =>
            replyClosed(c.replyTo)
          case c: Withdraw =>
            replyClosed(c.replyTo)
          case GetBalance(replyTo) =>
            Effect.reply(replyTo)(CurrentBalance(Zero))
          case CloseAccount(replyTo) =>
            replyClosed(replyTo)
          case CreateAccount(replyTo) =>
            replyClosed(replyTo)
        }

      private def replyClosed(replyTo: ActorRef[StatusReply[Done]]): ReplyEffect =
        Effect.reply(replyTo)(StatusReply.Error(s"Account is closed"))
    }

    // when used with sharding, this TypeKey can be used in `sharding.init` and `sharding.entityRefFor`:
    val TypeKey: EntityTypeKey[Command] =
      EntityTypeKey[Command]("Account")

    def apply(persistenceId: PersistenceId): Behavior[Command] =
      DurableStateBehavior.withEnforcedReplies[Command, Option[Account]](
        persistenceId,
        None,
        (state, cmd) =>
          state match {
            case None          => onFirstCommand(cmd)
            case Some(account) => account.applyCommand(cmd)
          })

    def onFirstCommand(cmd: Command): ReplyEffect =
      cmd match {
        case CreateAccount(replyTo) =>
          Effect.persist(Some(OpenedAccount(Zero))).thenReply(replyTo)(_ => StatusReply.Ack)
        case _ =>
          // CreateAccount before handling any other commands
          Effect.unhandled.thenNoReply()
      }
  }
  // #account-entity

}
