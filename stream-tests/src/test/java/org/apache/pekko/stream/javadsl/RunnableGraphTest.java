/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.stream.StreamTest;
import org.apache.pekko.stream.SystemMaterializer;
import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

public class RunnableGraphTest extends StreamTest {
  public RunnableGraphTest() {
    super(actorSystemResource);
  }

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("RunnableGraphTest", PekkoSpec.testConf());

  @Test
  public void beAbleToConvertFromJavaToScala() {
    final RunnableGraph<NotUsed> javaRunnable = Source.empty().to(Sink.ignore());
    final org.apache.pekko.stream.scaladsl.RunnableGraph<NotUsed> scalaRunnable =
        javaRunnable.asScala();
    assertEquals(
        NotUsed.getInstance(), scalaRunnable.run(SystemMaterializer.get(system).materializer()));
  }

  @Test
  public void beAbleToConvertFromScalaToJava() {
    final org.apache.pekko.stream.scaladsl.RunnableGraph<NotUsed> scalaRunnable =
        org.apache.pekko.stream.scaladsl.Source.empty()
            .to(org.apache.pekko.stream.scaladsl.Sink.ignore());
    final RunnableGraph<NotUsed> javaRunnable = scalaRunnable.asJava();
    assertEquals(NotUsed.getInstance(), javaRunnable.run(system));
  }
}
