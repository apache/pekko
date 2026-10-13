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

package org.apache.pekko.persistence;

import java.time.Duration;
import org.apache.pekko.actor.ActorRef;

/**
 * Subclassing {@link AbstractPersistentActorWithTimers} from Java has to keep compiling: both
 * {@code Timers} and {@code Eventsourced} implement the {@code aroundReceive}/{@code
 * aroundPreRestart}/ {@code aroundPostStop} members, and javac rejects a subclass unless the Scala
 * base class carries real (non-synthetic) overrides for them.
 */
@SuppressWarnings("unchecked")
public class JavaTimerPersistentActor extends AbstractPersistentActorWithTimers {

  public static final class Scheduled {
    public final Object msg;
    public final ActorRef replyTo;

    public Scheduled(Object msg, ActorRef replyTo) {
      this.msg = msg;
      this.replyTo = replyTo;
    }
  }

  private final String name;

  public JavaTimerPersistentActor(String name) {
    this.name = name;
  }

  @Override
  public String persistenceId() {
    return name;
  }

  @Override
  public Receive createReceiveRecover() {
    return receiveBuilder().matchAny(msg -> {}).build();
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(Scheduled.class, scheduled -> scheduled.replyTo.tell(scheduled.msg, getSelf()))
        .matchAny(
            msg -> {
              timers().startSingleTimer("key", new Scheduled(msg, getSender()), Duration.ZERO);
              persist(msg, evt -> {});
            })
        .build();
  }
}
