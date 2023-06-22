/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.persistence.testkit.query.javadsl.PersistenceTestKitReadJournal;
import org.apache.pekko.persistence.typed.ReplicaId;
import org.apache.pekko.persistence.typed.ReplicationId;
import org.apache.pekko.persistence.typed.crdt.Counter;
import org.apache.pekko.persistence.typed.javadsl.CommandHandler;
import org.apache.pekko.persistence.typed.javadsl.Effect;
import org.apache.pekko.persistence.typed.javadsl.EventHandler;
import org.apache.pekko.persistence.typed.javadsl.ReplicatedEventSourcedBehavior;
import org.apache.pekko.persistence.typed.javadsl.ReplicatedEventSourcing;
import org.apache.pekko.persistence.typed.javadsl.ReplicationContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

interface ReplicatedShoppingCartExample {

  // #shopping-cart
  public final class ShoppingCart
      extends ReplicatedEventSourcedBehavior<
          ShoppingCart.Command, ShoppingCart.Event, ShoppingCart.State> {

    public interface Event {}

    public static final class ItemUpdated implements Event {
      public final String productId;
      public final Counter.Updated update;

      public ItemUpdated(String productId, Counter.Updated update) {
        this.productId = productId;
        this.update = update;
      }
    }

    public interface Command {}

    public static final class AddItem implements Command {
      public final String productId;
      public final int count;

      public AddItem(String productId, int count) {
        this.productId = productId;
        this.count = count;
      }
    }

    public static final class RemoveItem implements Command {
      public final String productId;
      public final int count;

      public RemoveItem(String productId, int count) {
        this.productId = productId;
        this.count = count;
      }
    }

    public static class GetCartItems implements Command {
      public final ActorRef<CartItems> replyTo;

      public GetCartItems(ActorRef<CartItems> replyTo) {
        this.replyTo = replyTo;
      }
    }

    public static final class CartItems {
      public final Map<String, Integer> items;

      public CartItems(Map<String, Integer> items) {
        this.items = items;
      }
    }

    public static final class State {
      public final Map<String, Counter> items = new HashMap<>();
    }

    public static Behavior<Command> create(
        String entityId, ReplicaId replicaId, Set<ReplicaId> allReplicas) {
      return ReplicatedEventSourcing.commonJournalConfig(
          new ReplicationId("blog", entityId, replicaId),
          allReplicas,
          PersistenceTestKitReadJournal.Identifier(),
          ShoppingCart::new);
    }

    private ShoppingCart(ReplicationContext replicationContext) {
      super(replicationContext);
    }

    @Override
    public State emptyState() {
      return new State();
    }

    @Override
    public CommandHandler<Command, Event, State> commandHandler() {
      return newCommandHandlerBuilder()
          .forAnyState()
          .onCommand(AddItem.class, this::onAddItem)
          .onCommand(RemoveItem.class, this::onRemoveItem)
          .onCommand(GetCartItems.class, this::onGetCartItems)
          .build();
    }

    private Effect<Event, State> onAddItem(State state, AddItem command) {
      return Effect()
          .persist(new ItemUpdated(command.productId, new Counter.Updated(command.count)));
    }

    private Effect<Event, State> onRemoveItem(State state, RemoveItem command) {
      return Effect()
          .persist(new ItemUpdated(command.productId, new Counter.Updated(-command.count)));
    }

    private Effect<Event, State> onGetCartItems(State state, GetCartItems command) {
      command.replyTo.tell(new CartItems(filterEmptyAndNegative(state.items)));
      return Effect().none();
    }

    private Map<String, Integer> filterEmptyAndNegative(Map<String, Counter> cart) {
      Map<String, Integer> result = new HashMap<>();
      for (Map.Entry<String, Counter> entry : cart.entrySet()) {
        int count = entry.getValue().value().intValue();
        if (count > 0) result.put(entry.getKey(), count);
      }
      return Collections.unmodifiableMap(result);
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
      return newEventHandlerBuilder()
          .forAnyState()
          .onEvent(ItemUpdated.class, this::onItemUpdated)
          .build();
    }

    private State onItemUpdated(State state, ItemUpdated event) {
      final Counter counterForProduct;
      if (state.items.containsKey(event.productId)) {
        counterForProduct = state.items.get(event.productId);
      } else {
        counterForProduct = Counter.empty();
      }
      state.items.put(event.productId, counterForProduct.applyOperation(event.update));
      return state;
    }
  }
  // #shopping-cart
}
