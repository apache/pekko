/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl;

import org.apache.pekko.NotUsed;
import org.apache.pekko.japi.Pair;
import org.junit.Test;

import java.util.concurrent.Flow;

import org.scalatestplus.junit.JUnitSuite;

public class JavaFlowSupportCompileTest extends JUnitSuite {
  @Test
  public void shouldCompile() throws Exception {
    final Flow.Processor<String,String> processor = new Flow.Processor<String, String>() {
      @Override
      public void subscribe(Flow.Subscriber<? super String> subscriber) {}
      @Override
      public void onSubscribe(Flow.Subscription subscription) {}
      @Override
      public void onNext(String item) {}
      @Override
      public void onError(Throwable throwable) {}
      @Override
      public void onComplete() {}
    };


    final Source<String, Flow.Subscriber<String>> stringSubscriberSource =
      JavaFlowSupport.Source.asSubscriber();
    final Source<String, NotUsed> stringNotUsedSource =
      JavaFlowSupport.Source.fromPublisher(processor);

    final org.apache.pekko.stream.javadsl.Flow<String, String, NotUsed> stringStringNotUsedFlow =
      JavaFlowSupport.Flow.fromProcessor(() -> processor);
    final org.apache.pekko.stream.javadsl.Flow<String, String, NotUsed> stringStringNotUsedFlow1 =
      JavaFlowSupport.Flow.fromProcessorMat(() -> Pair.apply(processor, NotUsed.getInstance()));

    final Sink<String, Flow.Publisher<String>> stringPublisherSink =
      JavaFlowSupport.Sink.asPublisher(AsPublisher.WITH_FANOUT);
    final Sink<String, NotUsed> stringNotUsedSink =
      JavaFlowSupport.Sink.fromSubscriber(processor);
  }
}
