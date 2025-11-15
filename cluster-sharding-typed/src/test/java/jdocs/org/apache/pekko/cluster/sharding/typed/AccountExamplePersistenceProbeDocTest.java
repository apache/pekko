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

package jdocs.org.apache.pekko.cluster.sharding.typed;

import org.apache.pekko.Done;
import org.scalatestplus.junit.JUnitSuite;

import static jdocs.org.apache.pekko.cluster.sharding.typed.AccountExampleWithEventHandlersInState.AccountEntity;
import static org.junit.Assert.*;

// #test
import java.math.BigDecimal;

import org.apache.pekko.actor.testkit.typed.javadsl.BehaviorTestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.ReplyInbox;
import org.apache.pekko.actor.testkit.typed.javadsl.StatusReplyInbox;
import org.apache.pekko.persistence.testkit.javadsl.PersistenceProbeBehavior;
import org.apache.pekko.persistence.testkit.javadsl.PersistenceEffect;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.junit.Test;

public class AccountExamplePersistenceProbeDocTest
    // #test
    extends JUnitSuite
// #test
{
    @Test
    public void createWithEmptyBalance() {
        PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
            persistenceProbe = emptyAccount();

        BehaviorTestKit<AccountEntity.Command> testkit = persistenceProbe.getBehaviorTestKit();

        StatusReplyInbox<Done> ackInbox = testkit.runAskWithStatus(AccountEntity.CreateAccount::new);

        ackInbox.expectValue(Done.getInstance());
        persistenceProbe.getEventProbe().expectPersisted(AccountEntity.AccountCreated.INSTANCE);

        // internal state is only exposed by the behavior via responses to messages or if it happens
        //  to snapshot.  This particular behavior never snapshots, so we query within the actor's
        //  protocol
        assertFalse(persistenceProbe.getSnapshotProbe().hasEffects());

        ReplyInbox<AccountEntity.CurrentBalance> currentBalanceInbox =
            testkit.runAsk(AccountEntity.GetBalance::new);

        assertEquals(BigDecimal.ZERO, currentBalanceInbox.receiveReply().balance);
    }

    @Test
    public void handleDepositAndWithdraw() {
        PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
            persistenceProbe = openedAccount();

        BehaviorTestKit<AccountEntity.Command> testkit = persistenceProbe.getBehaviorTestKit();
        BigDecimal currentBalance;

        testkit
            .runAskWithStatus(
                Done.class, replyTo -> new AccountEntity.Deposit(BigDecimal.valueOf(100), replyTo))
            .expectValue(Done.getInstance());

        assertEquals(
            BigDecimal.valueOf(100),
            persistenceProbe
                .getEventProbe()
                .expectPersistedClass(AccountEntity.Deposited.class)
                .persistedObject()
                .amount);

        currentBalance =
            testkit
                .runAsk(AccountEntity.CurrentBalance.class, AccountEntity.GetBalance::new)
                .receiveReply()
                .balance;

        assertEquals(BigDecimal.valueOf(100), currentBalance);

        testkit
            .runAskWithStatus(
                Done.class, replyTo -> new AccountEntity.Withdraw(BigDecimal.valueOf(10), replyTo))
            .expectValue(Done.getInstance());

        // can save the persistence effect for in-depth inspection
        PersistenceEffect<AccountEntity.Withdrawn> withdrawEffect =
            persistenceProbe.getEventProbe().expectPersistedClass(AccountEntity.Withdrawn.class);
        assertEquals(BigDecimal.valueOf(10), withdrawEffect.persistedObject().amount);
        assertEquals(3L, withdrawEffect.sequenceNr());
        assertTrue(withdrawEffect.tags().isEmpty());

        currentBalance =
            testkit
                .runAsk(AccountEntity.CurrentBalance.class, AccountEntity.GetBalance::new)
                .receiveReply()
                .balance;

        assertEquals(BigDecimal.valueOf(90), currentBalance);
    }

    @Test
    public void rejectWithdrawOverdraft() {
        PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
            persistenceProbe = accountWithBalance(BigDecimal.valueOf(100));

        BehaviorTestKit<AccountEntity.Command> testkit = persistenceProbe.getBehaviorTestKit();

        testkit
            .runAskWithStatus(
                Done.class, replyTo -> new AccountEntity.Withdraw(BigDecimal.valueOf(110), replyTo))
            .expectErrorMessage("not enough funds to withdraw 110");

        assertFalse(persistenceProbe.getEventProbe().hasEffects());
    }

    // #test
    private PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
    emptyAccount() {
        return
            // #persistenceProbe-behavior
            PersistenceProbeBehavior.fromEventSourced(
                AccountEntity.create("1", PersistenceId.of("Account", "1")),
                null, // use the initial state
                0 // initial sequence number
            );
        // #persistenceProbe-behavior
    }

    private PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
    openedAccount() {
        return
            // #persistenceProbe-behavior-provided-state
            PersistenceProbeBehavior.fromEventSourced(
                AccountEntity.create("1", PersistenceId.of("Account", "1")),
                new AccountEntity.EmptyAccount()
                    .openedAccount(), // duplicate the event handler for AccountCreated on an EmptyAccount
                1 // assume that CreateAccount was the first command
            );
        // #persistenceProbe-behavior-provided-state
    }

    private PersistenceProbeBehavior<AccountEntity.Command, AccountEntity.Event, AccountEntity.Account>
    accountWithBalance(BigDecimal balance) {
        return PersistenceProbeBehavior.fromEventSourced(
            AccountEntity.create("1", PersistenceId.of("Account", "1")),
            new AccountEntity.OpenedAccount(balance),
            2);
    }
    // #test
}
// #test
