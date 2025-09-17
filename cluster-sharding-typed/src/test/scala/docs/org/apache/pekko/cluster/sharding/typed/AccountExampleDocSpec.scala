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

package docs.org.apache.pekko.cluster.sharding.typed

//#test
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }
import pekko.pattern.StatusReply
import pekko.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import pekko.persistence.typed.PersistenceId
//#test

import docs.org.apache.pekko.cluster.sharding.typed.AccountExampleWithEventHandlersInState.AccountEntity

//#test
//#testkit
class AccountExampleDocSpec
    extends ScalaTestWithActorTestKit(EventSourcedBehaviorTestKit.config)
    // #testkit
    with AnyWordSpecLike
    with BeforeAndAfterEach
    with LogCapturing {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[AccountEntity.Command, AccountEntity.Event, AccountEntity.Account](
      system,
      AccountEntity("1", PersistenceId("Account", "1")))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "Account" must {

    "be created with zero balance" in {
      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.CreateAccount(_))
      result.reply shouldBe StatusReply.Ack
      result.event shouldBe AccountEntity.AccountCreated
      result.stateOfType[AccountEntity.OpenedAccount].balance shouldBe 0
    }

    "handle Withdraw" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.CreateAccount(_))

      val result1 = eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.Deposit(100, _))
      result1.reply shouldBe StatusReply.Ack
      result1.event shouldBe AccountEntity.Deposited(100)
      result1.stateOfType[AccountEntity.OpenedAccount].balance shouldBe 100

      val result2 = eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.Withdraw(10, _))
      result2.reply shouldBe StatusReply.Ack
      result2.event shouldBe AccountEntity.Withdrawn(10)
      result2.stateOfType[AccountEntity.OpenedAccount].balance shouldBe 90
    }

    "reject Withdraw overdraft" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.CreateAccount(_))
      eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.Deposit(100, _))

      val result = eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.Withdraw(110, _))
      result.reply.isError shouldBe true
      result.hasNoEvents shouldBe true
    }

    "handle GetBalance" in {
      eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.CreateAccount(_))
      eventSourcedTestKit.runCommand[StatusReply[Done]](AccountEntity.Deposit(100, _))

      val result = eventSourcedTestKit.runCommand[AccountEntity.CurrentBalance](AccountEntity.GetBalance(_))
      result.reply.balance shouldBe 100
      result.hasNoEvents shouldBe true
    }
  }
}
//#test
