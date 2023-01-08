/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata;

import java.util.Optional;

// different delta type
public class JavaImplOfDeltaReplicatedData2
    extends AbstractDeltaReplicatedData<
        JavaImplOfDeltaReplicatedData2, JavaImplOfDeltaReplicatedData2.Delta> {

  public static class Delta extends AbstractReplicatedData<Delta>
      implements ReplicatedDelta, RequiresCausalDeliveryOfDeltas {
    @Override
    public Delta mergeData(Delta other) {
      return this;
    }

    @Override
    public JavaImplOfDeltaReplicatedData2 zero() {
      return new JavaImplOfDeltaReplicatedData2();
    }
  }

  @Override
  public JavaImplOfDeltaReplicatedData2 mergeData(JavaImplOfDeltaReplicatedData2 other) {
    return this;
  }

  @Override
  public JavaImplOfDeltaReplicatedData2 mergeDeltaData(Delta other) {
    return this;
  }

  @Override
  public Optional<Delta> deltaData() {
    return Optional.empty();
  }

  @Override
  public JavaImplOfDeltaReplicatedData2 resetDelta() {
    return this;
  }
}
