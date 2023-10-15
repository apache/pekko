/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.ddata;

import java.util.HashSet;
import java.util.Set;
import org.apache.pekko.cluster.ddata.AbstractReplicatedData;
import org.apache.pekko.cluster.ddata.GSet;

// #twophaseset
public class TwoPhaseSet extends AbstractReplicatedData<TwoPhaseSet> {

  public final GSet<String> adds;
  public final GSet<String> removals;

  public TwoPhaseSet(GSet<String> adds, GSet<String> removals) {
    this.adds = adds;
    this.removals = removals;
  }

  public static TwoPhaseSet create() {
    return new TwoPhaseSet(GSet.create(), GSet.create());
  }

  public TwoPhaseSet add(String element) {
    return new TwoPhaseSet(adds.add(element), removals);
  }

  public TwoPhaseSet remove(String element) {
    return new TwoPhaseSet(adds, removals.add(element));
  }

  public Set<String> getElements() {
    Set<String> result = new HashSet<>(adds.getElements());
    result.removeAll(removals.getElements());
    return result;
  }

  @Override
  public TwoPhaseSet mergeData(TwoPhaseSet that) {
    return new TwoPhaseSet(this.adds.merge(that.adds), this.removals.merge(that.removals));
  }
}
// #twophaseset
