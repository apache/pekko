package docs.actors.classical;
// #pattern-matching

import org.apache.pekko.actor.UntypedAbstractActor;

public class OptimizedActorWithJava21 extends UntypedAbstractActor {
    public static class Msg1 {
    }

    public static class Msg2 {
    }

    public static class Msg3 {
    }

    @Override
    public void onReceive(Object msg) throws Exception {
        switch (msg) {
            case Msg1 msg1 -> receiveMsg1(msg1);
            case Msg2 msg2 -> receiveMsg2(msg2);
            case Msg3 msg3 -> receiveMsg3(msg3);
            default -> unhandled(msg);
        }
    }

    private void receiveMsg1(Msg1 msg) {
        // actual work
    }

    private void receiveMsg2(Msg2 msg) {
        // actual work
    }

    private void receiveMsg3(Msg3 msg) {
        // actual work
    }
}
// #pattern-matching
