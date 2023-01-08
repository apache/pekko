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

// same delta type
public class JavaImplOfDeltaReplicatedData
    extends AbstractDeltaReplicatedData<
        JavaImplOfDeltaReplicatedData, JavaImplOfDeltaReplicatedData>
    implements ReplicatedDelta {

  @Override
  public JavaImplOfDeltaReplicatedData mergeData(JavaImplOfDeltaReplicatedData other) {
    return this;
  }

  @Override
  public JavaImplOfDeltaReplicatedData mergeDeltaData(JavaImplOfDeltaReplicatedData other) {
    return this;
  }

  @Override
  public Optional<JavaImplOfDeltaReplicatedData> deltaData() {
    return Optional.empty();
  }

  @Override
  public JavaImplOfDeltaReplicatedData resetDelta() {
    return this;
  }

  @Override
  public JavaImplOfDeltaReplicatedData zero() {
    return new JavaImplOfDeltaReplicatedData();
  }
}
