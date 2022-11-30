/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream.javadsl.cookbook;

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.apache.pekko.util.ByteString;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Duration;

public class RecipeKeepAlive extends RecipeTest {
  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("RecipeKeepAlive");
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }

  class Tick {}

  public final Tick TICK = new Tick();

  @Test
  public void workForVersion1() throws Exception {
    new TestKit(system) {
      {
        final ByteString keepAliveMessage = ByteString.fromArray(new byte[] {11});

        // @formatter:off
        // #inject-keepalive
        Flow<ByteString, ByteString, NotUsed> keepAliveInject =
            Flow.of(ByteString.class).keepAlive(Duration.ofSeconds(1), () -> keepAliveMessage);
        // #inject-keepalive
        // @formatter:on

        // Enough to compile, tested elsewhere as a built-in stage
      }
    };
  }
}
