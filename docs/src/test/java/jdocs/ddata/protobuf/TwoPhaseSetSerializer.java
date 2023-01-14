/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.ddata.protobuf;

// #serializer
import jdocs.ddata.TwoPhaseSet;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages;
import docs.ddata.protobuf.msg.TwoPhaseSetMessages.TwoPhaseSet.Builder;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.pekko.actor.ExtendedActorSystem;
import org.apache.pekko.cluster.ddata.GSet;
import org.apache.pekko.cluster.ddata.protobuf.AbstractSerializationSupport;

public class TwoPhaseSetSerializer extends AbstractSerializationSupport {

  private final ExtendedActorSystem system;

  public TwoPhaseSetSerializer(ExtendedActorSystem system) {
    this.system = system;
  }

  @Override
  public ExtendedActorSystem system() {
    return this.system;
  }

  @Override
  public boolean includeManifest() {
    return false;
  }

  @Override
  public int identifier() {
    return 99998;
  }

  @Override
  public byte[] toBinary(Object obj) {
    if (obj instanceof TwoPhaseSet) {
      return twoPhaseSetToProto((TwoPhaseSet) obj).toByteArray();
    } else {
      throw new IllegalArgumentException("Can't serialize object of type " + obj.getClass());
    }
  }

  @Override
  public Object fromBinaryJava(byte[] bytes, Class<?> manifest) {
    return twoPhaseSetFromBinary(bytes);
  }

  protected TwoPhaseSetMessages.TwoPhaseSet twoPhaseSetToProto(TwoPhaseSet twoPhaseSet) {
    Builder b = TwoPhaseSetMessages.TwoPhaseSet.newBuilder();
    ArrayList<String> adds = new ArrayList<>(twoPhaseSet.adds.getElements());
    if (!adds.isEmpty()) {
      Collections.sort(adds);
      b.addAllAdds(adds);
    }
    ArrayList<String> removals = new ArrayList<>(twoPhaseSet.removals.getElements());
    if (!removals.isEmpty()) {
      Collections.sort(removals);
      b.addAllRemovals(removals);
    }
    return b.build();
  }

  protected TwoPhaseSet twoPhaseSetFromBinary(byte[] bytes) {
    try {
      TwoPhaseSetMessages.TwoPhaseSet msg = TwoPhaseSetMessages.TwoPhaseSet.parseFrom(bytes);
      GSet<String> adds = GSet.create();
      for (String elem : msg.getAddsList()) {
        adds = adds.add(elem);
      }
      GSet<String> removals = GSet.create();
      for (String elem : msg.getRemovalsList()) {
        removals = removals.add(elem);
      }
      // GSet will accumulate deltas when adding elements,
      // but those are not of interest in the result of the deserialization
      return new TwoPhaseSet(adds.resetDelta(), removals.resetDelta());
    } catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
}
// #serializer
