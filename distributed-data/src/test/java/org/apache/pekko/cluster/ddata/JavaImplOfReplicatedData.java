/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata;

import org.apache.pekko.cluster.UniqueAddress;

public class JavaImplOfReplicatedData extends AbstractReplicatedData<JavaImplOfReplicatedData>
    implements RemovedNodePruning {

  @Override
  public JavaImplOfReplicatedData mergeData(JavaImplOfReplicatedData other) {
    return this;
  }

  @Override
  public scala.collection.immutable.Set<UniqueAddress> modifiedByNodes() {
    return org.apache.pekko.japi.Util.immutableSeq(new java.util.ArrayList<UniqueAddress>())
        .toSet();
  }

  @Override
  public boolean needPruningFrom(UniqueAddress removedNode) {
    return false;
  }

  @Override
  public JavaImplOfReplicatedData prune(UniqueAddress removedNode, UniqueAddress collapseInto) {
    return this;
  }

  @Override
  public JavaImplOfReplicatedData pruningCleanup(UniqueAddress removedNode) {
    return this;
  }
}
