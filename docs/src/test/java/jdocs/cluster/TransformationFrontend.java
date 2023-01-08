/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.cluster;

import static jdocs.cluster.TransformationMessages.BACKEND_REGISTRATION;

import java.util.ArrayList;
import java.util.List;

import jdocs.cluster.TransformationMessages.JobFailed;
import jdocs.cluster.TransformationMessages.TransformationJob;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Terminated;
import org.apache.pekko.actor.AbstractActor;

// #frontend
public class TransformationFrontend extends AbstractActor {

  List<ActorRef> backends = new ArrayList<ActorRef>();
  int jobCounter = 0;

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            TransformationJob.class,
            job -> backends.isEmpty(),
            job -> {
              getSender()
                  .tell(new JobFailed("Service unavailable, try again later", job), getSender());
            })
        .match(
            TransformationJob.class,
            job -> {
              jobCounter++;
              backends.get(jobCounter % backends.size()).forward(job, getContext());
            })
        .matchEquals(
            BACKEND_REGISTRATION,
            x -> {
              getContext().watch(getSender());
              backends.add(getSender());
            })
        .match(
            Terminated.class,
            terminated -> {
              backends.remove(terminated.getActor());
            })
        .build();
  }
}
// #frontend
