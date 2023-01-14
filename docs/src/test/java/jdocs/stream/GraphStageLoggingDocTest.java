/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.stream;

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.Attributes;
import org.apache.pekko.stream.Materializer;
import org.apache.pekko.stream.Outlet;
import org.apache.pekko.stream.SourceShape;
// #stage-with-logging
import org.apache.pekko.stream.stage.AbstractOutHandler;
import org.apache.pekko.stream.stage.GraphStage;
import org.apache.pekko.stream.stage.GraphStageLogic;
import org.apache.pekko.stream.stage.GraphStageLogicWithLogging;

// #stage-with-logging
import jdocs.AbstractJavaTest;
import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;

public class GraphStageLoggingDocTest extends AbstractJavaTest {
  static ActorSystem system;
  static Materializer mat;

  @Test
  public void compileOnlyTestClass() throws Exception {}

  // #operator-with-logging
  public class RandomLettersSource extends GraphStage<SourceShape<String>> {
    public final Outlet<String> out = Outlet.create("RandomLettersSource.in");

    private final SourceShape<String> shape = SourceShape.of(out);

    @Override
    public SourceShape<String> shape() {
      return shape;
    }

    @Override
    public GraphStageLogic createLogic(Attributes inheritedAttributes) {
      return new GraphStageLogicWithLogging(shape()) {

        {
          setHandler(
              out,
              new AbstractOutHandler() {
                @Override
                public void onPull() throws Exception {
                  final String s = nextChar(); // ASCII lower case letters

                  // `log` is obtained from materializer automatically (via StageLogging)
                  log().debug("Randomly generated: [{}]", s);

                  push(out, s);
                }

                private String nextChar() {
                  final char i = (char) ThreadLocalRandom.current().nextInt('a', 'z' + 1);
                  return String.valueOf(i);
                }
              });
        }
      };
    }
  }
  // #operator-with-logging

}
