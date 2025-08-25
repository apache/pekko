/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.journal.japi;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.apache.pekko.persistence.PersistentRepr;

interface AsyncRecoveryPlugin {
  // #async-replay-plugin-api
  /**
   * Java API, Plugin API: asynchronously replays persistent messages. Implementations replay a
   * message by calling `replayCallback`. The returned future must be completed when all messages
   * (matching the sequence number bounds) have been replayed. The future must be completed with a
   * failure if any of the persistent messages could not be replayed.
   *
   * <p>The `replayCallback` must also be called with messages that have been marked as deleted. In
   * this case a replayed message's `deleted` method must return `true`.
   *
   * <p>The `toSequenceNr` is the lowest of what was returned by {@link
   * #doAsyncReadHighestSequenceNr} and what the user specified as recovery {@link
   * org.apache.pekko.persistence.Recovery} parameter.
   *
   * @param persistenceId id of the persistent actor.
   * @param fromSequenceNr sequence number where replay should start (inclusive).
   * @param toSequenceNr sequence number where replay should end (inclusive).
   * @param max maximum number of messages to be replayed.
   * @param replayCallback called to replay a single message. Can be called from any thread.
   * @return a CompletionStage that will be completed when the replay is done (in Pekko 1.x, this
   *     was a Scala Future)
   */
  CompletionStage<Void> doAsyncReplayMessages(
      String persistenceId,
      long fromSequenceNr,
      long toSequenceNr,
      long max,
      Consumer<PersistentRepr> replayCallback);

  /**
   * Java API, Plugin API: asynchronously reads the highest stored sequence number for the given
   * `persistenceId`. The persistent actor will use the highest sequence number after recovery as
   * the starting point when persisting new events. This sequence number is also used as
   * `toSequenceNr` in subsequent call to [[#asyncReplayMessages]] unless the user has specified a
   * lower `toSequenceNr`.
   *
   * @param persistenceId id of the persistent actor.
   * @param fromSequenceNr hint where to start searching for the highest sequence number.
   * @return a CompletionStage that will be completed when the read is done (in Pekko 1.x, this was
   *     a Scala Future)
   */
  CompletionStage<Long> doAsyncReadHighestSequenceNr(String persistenceId, long fromSequenceNr);
  // #async-replay-plugin-api
}
