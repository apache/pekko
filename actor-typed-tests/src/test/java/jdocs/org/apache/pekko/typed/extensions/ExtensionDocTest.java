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

package jdocs.org.apache.pekko.typed.extensions;

import docs.org.apache.pekko.typed.extensions.DatabasePool;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.Extension;
import org.apache.pekko.actor.typed.ExtensionId;
import org.apache.pekko.actor.typed.javadsl.Behaviors;

interface ExtensionDocTest {

  // #shared-resource
  public class ExpensiveDatabaseConnection {
    public CompletionStage<Object> executeQuery(String query) {
      throw new RuntimeException("I should do a database query");
    }
    // ...
  }
  // #shared-resource

  // #extension
  public class DatabaseConnectionPool implements Extension {
    // #extension
    // #extension-id
    public static class Id extends ExtensionId<DatabaseConnectionPool> {

      private static final Id instance = new Id();

      private Id() {}

      // called once per ActorSystem
      @Override
      public DatabaseConnectionPool createExtension(ActorSystem<?> system) {
        return new DatabaseConnectionPool(system);
      }

      public static DatabaseConnectionPool get(ActorSystem<?> system) {
        return instance.apply(system);
      }
    }
    // #extension-id
    // #extension

    private final ExpensiveDatabaseConnection _connection;

    private DatabaseConnectionPool(ActorSystem<?> system) {
      // database configuration can be loaded from config
      // from the actor system
      _connection = new ExpensiveDatabaseConnection();
    }

    public ExpensiveDatabaseConnection connection() {
      return _connection;
    }
  }
  // #extension

  public static Behavior<Object> initialBehavior() {
    return null;
  }

  public static void usage() {
    // #usage
    Behaviors.setup(
        (context) -> {
          DatabaseConnectionPool.Id.get(context.getSystem())
              .connection()
              .executeQuery("insert into...");
          return initialBehavior();
        });
    // #usage
  }

  public static void checkScalaExtension() {
    ActorSystem<?> system = null;
    DatabasePool.get(system);
  }
}
