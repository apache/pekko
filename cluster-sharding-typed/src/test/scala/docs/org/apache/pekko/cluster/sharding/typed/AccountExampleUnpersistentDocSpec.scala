/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.cluster.sharding.typed

import org.apache.pekko
import pekko.Done
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

// #test
import pekko.persistence.testkit.scaladsl.UnpersistentBehavior
import pekko.persistence.typed.PersistenceId

class AccountExampleUnpersistentDocSpec
    extends AnyWordSpecLike
    // #test
    with Matchers
    // #test
    {
  // #test
  import AccountExampleWithEventHandlersInState.AccountEntity
  // #test
  "Account" must {
    "be created with zero balance" in {
      onAnEmptyAccount { (testkit, eventProbe, snapshotProbe) =>
        testkit.runAskWithStatus[Done](AccountEntity.CreateAccount(_)).expectDone()

        eventProbe.expectPersisted(AccountEntity.AccountCreated)

        // internal state is only exposed by the behavior via responses to messages or if it happens
        //  to snapshot.  This particular behavior never snapshots, so we query within the actor's
        //  protocol
        snapshotProbe.hasEffects shouldBe false

        testkit.runAsk[AccountEntity.CurrentBalance](AccountEntity.GetBalance(_)).receiveReply().balance shouldBe 0
      }
    }

    "handle Deposit and Withdraw" in {
      onAnOpenedAccount { (testkit, eventProbe, _) =>
        testkit.runAskWithStatus[Done](AccountEntity.Deposit(100, _)).expectDone()

        eventProbe.expectPersisted(AccountEntity.Deposited(100))

        testkit.runAskWithStatus[Done](AccountEntity.Withdraw(10, _)).expectDone()

        eventProbe.expectPersisted(AccountEntity.Withdrawn(10))

        testkit.runAsk[AccountEntity.CurrentBalance](AccountEntity.GetBalance(_)).receiveReply().balance shouldBe 90
      }
    }

    "reject Withdraw overdraft" in {
      onAnAccountWithBalance(100) { (testkit, eventProbe, _) =>
        testkit.runAskWithStatus(AccountEntity.Withdraw(110, _)).receiveStatusReply().isError shouldBe true

        eventProbe.hasEffects shouldBe false
      }
    }
  }
  // #test

  // #unpersistent-behavior
  private def onAnEmptyAccount
      : UnpersistentBehavior.EventSourced[AccountEntity.Command, AccountEntity.Event, AccountEntity.Account] =
    UnpersistentBehavior.fromEventSourced(AccountEntity("1", PersistenceId("Account", "1")))
  // #unpersistent-behavior

  // #unpersistent-behavior-provided-state
  private def onAnOpenedAccount
      : UnpersistentBehavior.EventSourced[AccountEntity.Command, AccountEntity.Event, AccountEntity.Account] =
    UnpersistentBehavior.fromEventSourced(
      AccountEntity("1", PersistenceId("Account", "1")),
      Some(
        AccountEntity.EmptyAccount.applyEvent(AccountEntity.AccountCreated) -> // reuse the event handler
        1L // assume that CreateAccount was the first command
      ))
  // #unpersistent-behavior-provided-state

  private def onAnAccountWithBalance(balance: BigDecimal) =
    UnpersistentBehavior.fromEventSourced(
      AccountEntity("1", PersistenceId("Account", "1")),
      Some(AccountEntity.OpenedAccount(balance) -> 2L))
  // #test
}
// #test
