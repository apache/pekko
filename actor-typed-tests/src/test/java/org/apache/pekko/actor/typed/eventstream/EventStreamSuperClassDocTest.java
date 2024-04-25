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

package org.apache.pekko.actor.typed.eventstream;

import org.apache.pekko.actor.testkit.typed.javadsl.ActorTestKit;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;

// #listen-to-super-class-imports
import org.apache.pekko.actor.DeadLetter;
import org.apache.pekko.actor.AllDeadLetters;
import org.apache.pekko.actor.Dropped;
import org.apache.pekko.actor.SuppressedDeadLetter;
import org.apache.pekko.actor.UnhandledMessage;
// #listen-to-super-class-imports

public class EventStreamSuperClassDocTest extends JUnitSuite {

  @Test
  public void listenToDeadLetters() {
    // #subscribe-to-super-class
    ActorSystem<Command> system =
        ActorSystem.create(AllDeadLettersListenerBehavior.create(), "AllDeadLettersListener");
    // #subscribe-to-super-class
    ActorTestKit.shutdown(system);
  }

  // #listen-to-super-class
  interface Command {}

  static final class AllDeadLettersWrapper implements Command {
    private final AllDeadLetters allDeadLetters;

    public AllDeadLettersWrapper(AllDeadLetters deadLetter) {
      this.allDeadLetters = deadLetter;
    }

    public AllDeadLetters getAllDeadLetters() {
      return allDeadLetters;
    }
  }

  static class AllDeadLettersListenerBehavior extends AbstractBehavior<Command> {

    public static Behavior<Command> create() {
      return Behaviors.setup(AllDeadLettersListenerBehavior::new);
    }

    public AllDeadLettersListenerBehavior(ActorContext<Command> context) {
      super(context);
      ActorRef<AllDeadLetters> messageAdapter =
          context.messageAdapter(
              AllDeadLetters.class, EventStreamSuperClassDocTest.AllDeadLettersWrapper::new);
      context
          .getSystem()
          .eventStream()
          .tell(new EventStream.Subscribe<>(AllDeadLetters.class, messageAdapter));
    }

    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          .onMessage(
              AllDeadLettersWrapper.class,
              msg -> {
                final AllDeadLetters allDeadLetters = msg.getAllDeadLetters();
                final Class<? extends AllDeadLetters> klass = msg.allDeadLetters.getClass();
                if (klass.isAssignableFrom(DeadLetter.class)) {
                  final DeadLetter deadLetter = (DeadLetter) allDeadLetters;
                  getContext()
                      .getLog()
                      .info(
                          "DeadLetter: sender ({}) to recipient ({}) with message: {}",
                          deadLetter.sender().path().name(),
                          deadLetter.recipient().path().name(),
                          deadLetter.message());
                } else if (klass.isAssignableFrom(Dropped.class)) {
                  final Dropped dropped = (Dropped) allDeadLetters;
                  getContext()
                      .getLog()
                      .info(
                          "Dropped: sender ({}) to recipient ({}) with message: {}, reason: {}",
                          dropped.sender().path().name(),
                          dropped.recipient().path().name(),
                          dropped.message(),
                          dropped.reason());
                } else if (klass.isAssignableFrom(SuppressedDeadLetter.class)) {
                  final SuppressedDeadLetter suppressedDeadLetter =
                      (SuppressedDeadLetter) allDeadLetters;
                  getContext()
                      .getLog()
                      .trace(
                          "SuppressedDeadLetter: sender ({}) to recipient ({}) with message: {}",
                          suppressedDeadLetter.sender().path().name(),
                          suppressedDeadLetter.recipient().path().name(),
                          suppressedDeadLetter.message());
                } else if (klass.isAssignableFrom(UnhandledMessage.class)) {
                  final UnhandledMessage unhandledMessage = (UnhandledMessage) allDeadLetters;
                  getContext()
                      .getLog()
                      .info(
                          "UnhandledMessage: sender ({}) to recipient ({}) with message: {}",
                          unhandledMessage.sender().path().name(),
                          unhandledMessage.recipient().path().name(),
                          unhandledMessage.message());
                }
                return Behaviors.same();
              })
          .build();
    }
  }
  // #listen-to-super-class
}
