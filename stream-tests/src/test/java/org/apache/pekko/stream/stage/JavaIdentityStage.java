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

package org.apache.pekko.stream.stage;

import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.FlowShape;
import org.apache.pekko.stream.Inlet;
import org.apache.pekko.stream.Outlet;

public class JavaIdentityStage<T> extends GraphStage<FlowShape<T, T>> {
  private Inlet<T> _in = Inlet.create("Identity.in");
  private Outlet<T> _out = Outlet.create("Identity.out");
  private FlowShape<T, T> _shape = FlowShape.of(_in, _out);

  public Inlet<T> in() {
    return _in;
  }

  public Outlet<T> out() {
    return _out;
  }

  @Override
  public GraphStageLogic createLogic(Attributes inheritedAttributes) {
    return new GraphStageLogic(shape()) {

      {
        setHandler(
            in(),
            new AbstractInHandler() {
              @Override
              public void onPush() {
                push(out(), grab(in()));
              }
            });

        setHandler(
            out(),
            new AbstractOutHandler() {
              @Override
              public void onPull() {
                pull(in());
              }
            });
      }
    };
  }

  @Override
  public FlowShape<T, T> shape() {
    return _shape;
  }
}
