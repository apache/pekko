/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.org.apache.pekko.persistence.typed;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The model from org.apache.pekko.persistence.fsm.AbstractPersistentFSMTest.WebStoreCustomerFSM
 * brought here for the PersistentFsmToTypedMigrationCompileOnlyTest
 */
public class WebStoreCustomerFSM {
  public static class ShoppingCart {
    private final List<Item> items = new ArrayList<>();

    public ShoppingCart(Item initialItem) {
      items.add(initialItem);
    }

    public ShoppingCart() {}

    public List<Item> getItems() {
      return List.copyOf(items);
    }

    public ShoppingCart addItem(Item item) {
      items.add(item);
      return this;
    }

    public void empty() {
      items.clear();
    }
  }

  public record Item(String id, String name, float price) implements Serializable {
    @Override
    public String toString() {
      return "Item{id=%s, name=%s, price=%s}".formatted(id, price, name);
    }
  }

  public interface Command {}

  public record AddItem(Item item) implements Command {}

  public enum Buy implements Command {
    INSTANCE
  }

  public enum Leave implements Command {
    INSTANCE
  }

  public enum GetCurrentCart implements Command {
    INSTANCE
  }

  public interface DomainEvent extends Serializable {}

  public record ItemAdded(Item item) implements DomainEvent {}

  public enum OrderExecuted implements DomainEvent {
    INSTANCE
  }

  public enum OrderDiscarded implements DomainEvent {
    INSTANCE
  }

  public enum CustomerInactive implements DomainEvent {
    INSTANCE
  }

  // Side effects - report events to be sent to some "Report Actor"
  public interface ReportEvent {}

  public record PurchaseWasMade(List<Item> items) implements ReportEvent {}

  public enum ShoppingCardDiscarded implements ReportEvent {
    INSTANCE
  }
}
