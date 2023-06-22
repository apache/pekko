/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.io.japi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public class EchoServer {

  public static void main(String[] args) throws InterruptedException {
    final Config config = ConfigFactory.parseString("pekko.loglevel=DEBUG");
    final ActorSystem system = ActorSystem.create("EchoServer", config);
    try {
      final CountDownLatch latch = new CountDownLatch(1);
      final ActorRef watcher = system.actorOf(Props.create(Watcher.class, latch), "watcher");
      final ActorRef nackServer =
          system.actorOf(Props.create(EchoManager.class, EchoHandler.class), "nack");
      final ActorRef ackServer =
          system.actorOf(Props.create(EchoManager.class, SimpleEchoHandler.class), "ack");
      watcher.tell(nackServer, ActorRef.noSender());
      watcher.tell(ackServer, ActorRef.noSender());
      latch.await(10, TimeUnit.MINUTES);
    } finally {
      system.terminate();
    }
  }
}
