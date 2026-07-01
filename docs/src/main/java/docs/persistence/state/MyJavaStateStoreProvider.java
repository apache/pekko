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

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.persistence.state.DurableStateStoreProvider;
import org.apache.pekko.persistence.state.scaladsl.DurableStateStore;
import com.typesafe.config.Config;

// #plugin-provider
class MyJavaStateStoreProvider implements DurableStateStoreProvider {

  private ExtendedActorSystem system;
  private Config config;
  private String cfgPath;

  public MyJavaStateStoreProvider(ExtendedActorSystem system, Config config, String cfgPath) {
    this.system = system;
    this.config = config;
    this.cfgPath = cfgPath;
  }

  @Override
  public DurableStateStore<Object> scaladslDurableStateStore() {
    return new MyStateStore<>(this.system, this.config, this.cfgPath);
  }

  @Override
  public org.apache.pekko.persistence.state.javadsl.DurableStateStore<Object>
      javadslDurableStateStore() {
    return new MyJavaStateStore<>(this.system, this.config, this.cfgPath);
  }
}
// #plugin-provider
