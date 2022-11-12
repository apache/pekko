/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.testkit.TestPublisher;
import org.apache.pekko.stream.testkit.TestSubscriber;
import org.apache.pekko.stream.testkit.javadsl.TestSink;
import org.apache.pekko.stream.testkit.javadsl.TestSource;
import org.apache.pekko.testkit.TestLatch;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class RecipeSimpleDrop extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeSimpleDrop");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  @Test
  public void work() throws Exception {
    new TestKit(system) {
      {
        @SuppressWarnings("unused")
        // #simple-drop
        final Flow<Message, Message, NotUsed> droppyStream =
            Flow.of(Message.class).conflate((lastMessage, newMessage) -> newMessage);
        // #simple-drop
        final TestLatch latch = new TestLatch(2, system);
        final Flow<Message, Message, NotUsed> realDroppyStream =
            Flow.of(Message.class)
                .conflate(
                    (lastMessage, newMessage) -> {
                      latch.countDown();
                      return newMessage;
                    });

        final Pair<TestPublisher.Probe<Message>, TestSubscriber.Probe<Message>> pubSub =
            TestSource.<Message>probe(system)
                .via(realDroppyStream)
                .toMat(TestSink.probe(system), (pub, sub) -> new Pair<>(pub, sub))
                .run(system);
        final TestPublisher.Probe<Message> pub = pubSub.first();
        final TestSubscriber.Probe<Message> sub = pubSub.second();

        pub.sendNext(new Message("1"));
        pub.sendNext(new Message("2"));
        pub.sendNext(new Message("3"));

        Await.ready(latch, Duration.create(1, TimeUnit.SECONDS));

        sub.requestNext(new Message("3"));

        pub.sendComplete();
        sub.request(1);
        sub.expectComplete();
      }
    };
  }
}
