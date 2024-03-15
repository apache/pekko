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
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.junit.Test;
import org.scalatestplus.junit.JUnitSuite;
// #dead-letter-imports
import org.apache.pekko.actor.DeadLetter;
import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.ActorSystem;
// #dead-letter-imports

public class EventStreamDocTest extends JUnitSuite {

    @Test
    public void listenToDeadLetters() {
        // #subscribe-to-dead-letters
        ActorSystem<Command> system = ActorSystem.create(DeadLetterListenerBehavior.create(), "DeadLetterListener");
        system.eventStream().tell(new EventStream.Subscribe<>(DeadLetter.class, system));
        // #subscribe-to-dead-letters
        ActorTestKit.shutdown(system);
    }

    // #listen-to-dead-letters
    interface Command {}

    static final class DeadLetterWrapper implements Command {
        private final DeadLetter deadLetter;

        public DeadLetterWrapper(DeadLetter deadLetter) {
            this.deadLetter = deadLetter;
        }

        public DeadLetter getDeadLetter() {
            return deadLetter;
        }
    }

    static class DeadLetterListenerBehavior extends AbstractBehavior<Command> {

        public static Behavior<Command> create() {
            return Behaviors.setup(DeadLetterListenerBehavior::new);
        }

        public DeadLetterListenerBehavior(ActorContext<Command> context) {
            super(context);
            ActorRef<DeadLetter> messageAdapter = context.messageAdapter(DeadLetter.class, DeadLetterWrapper::new);
            context.getSystem().eventStream().tell(new EventStream.Subscribe<>(DeadLetter.class, messageAdapter));
        }

        @Override
        public Receive<Command> createReceive() {
            return newReceiveBuilder().onMessage(DeadLetterWrapper.class, msg -> {
                final DeadLetter deadLetter = msg.getDeadLetter();
                getContext().getLog().info("Dead letter received from sender ({}) to recipient ({}) with message: {}",
                        deadLetter.sender().path().name(),
                        deadLetter.recipient().path().name(),
                        deadLetter.message().toString());
                return Behaviors.same();
            }).build();
        }
    }
    // #listen-to-dead-letters
}
