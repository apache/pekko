/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence.state;

// #plugin-imports

import org.apache.pekko.Done;
import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.state.javadsl.DurableStateUpdateStore;
import org.apache.pekko.persistence.state.javadsl.GetObjectResult;
import com.typesafe.config.Config;
import java.util.concurrent.CompletionStage;

// #plugin-imports

// #state-store-plugin-api
class MyJavaStateStore<A> implements DurableStateUpdateStore<A> {

  private ExtendedActorSystem system;
  private Config config;
  private String cfgPath;

  public MyJavaStateStore(ExtendedActorSystem system, Config config, String cfgPath) {
    this.system = system;
    this.config = config;
    this.cfgPath = cfgPath;
  }

  /** Returns the current state for the given persistence id. */
  @Override
  public CompletionStage<GetObjectResult<A>> getObject(String persistenceId) {
    // implement getObject here
    return null;
  }

  /**
   * Will persist the latest state. If it's a new persistence id, the record will be inserted.
   *
   * <p>In case of an existing persistence id, the record will be updated only if the revision
   * number of the incoming record is 1 more than the already existing record. Otherwise persist
   * will fail.
   */
  @Override
  public CompletionStage<Done> upsertObject(
      String persistenceId, long revision, A value, String tag) {
    // implement upsertObject here
    return null;
  }

  /** Deprecated. Use the deleteObject overload with revision instead. */
  @SuppressWarnings("deprecation")
  @Override
  public CompletionStage<Done> deleteObject(String persistenceId) {
    return deleteObject(persistenceId, 0);
  }

  /**
   * Will delete the state by setting it to the empty state and the revision number will be
   * incremented by 1.
   */
  @Override
  public CompletionStage<Done> deleteObject(String persistenceId, long revision) {
    // implement deleteObject here
    return null;
  }
}
// #state-store-plugin-api
