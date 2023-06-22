/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.cluster.sharding.typed;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.AbstractBehavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import org.apache.pekko.actor.typed.javadsl.Receive;
import org.apache.pekko.cluster.sharding.typed.javadsl.ClusterSharding;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityRef;
import org.apache.pekko.cluster.sharding.typed.javadsl.EntityTypeKey;

interface ShardingReplyCompileOnlyTest {

  // #sharded-response
  // a sharded actor that needs counter updates
  public class CounterConsumer {
    public static EntityTypeKey<Command> typeKey =
        EntityTypeKey.create(Command.class, "example-sharded-response");

    public interface Command {}

    public static class NewCount implements Command {
      public final long value;

      public NewCount(long value) {
        this.value = value;
      }
    }
  }

  // a sharded counter that sends responses to another sharded actor
  public class Counter extends AbstractBehavior<Counter.Command> {
    public static EntityTypeKey<Command> typeKey =
        EntityTypeKey.create(Command.class, "example-sharded-counter");

    public interface Command {}

    public enum Increment implements Command {
      INSTANCE
    }

    public static class GetValue implements Command {
      public final String replyToEntityId;

      public GetValue(String replyToEntityId) {
        this.replyToEntityId = replyToEntityId;
      }
    }

    public static Behavior<Command> create() {
      return Behaviors.setup(Counter::new);
    }

    private final ClusterSharding sharding;
    private int value = 0;

    private Counter(ActorContext<Command> context) {
      super(context);
      this.sharding = ClusterSharding.get(context.getSystem());
    }

    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          .onMessage(Increment.class, msg -> onIncrement())
          .onMessage(GetValue.class, this::onGetValue)
          .build();
    }

    private Behavior<Command> onIncrement() {
      value++;
      return this;
    }

    private Behavior<Command> onGetValue(GetValue msg) {
      EntityRef<CounterConsumer.Command> entityRef =
          sharding.entityRefFor(CounterConsumer.typeKey, msg.replyToEntityId);
      entityRef.tell(new CounterConsumer.NewCount(value));
      return this;
    }
  }
  // #sharded-response
}
