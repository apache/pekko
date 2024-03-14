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
