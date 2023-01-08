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

package jdocs.io.japi;

import java.net.InetSocketAddress;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.SupervisorStrategy;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.io.Tcp;
import org.apache.pekko.io.Tcp.Bind;
import org.apache.pekko.io.Tcp.Bound;
import org.apache.pekko.io.Tcp.Connected;
import org.apache.pekko.io.TcpMessage;

public class EchoManager extends AbstractActor {

  final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), getSelf());

  final Class<?> handlerClass;

  public EchoManager(Class<?> handlerClass) {
    this.handlerClass = handlerClass;
  }

  @Override
  public SupervisorStrategy supervisorStrategy() {
    return SupervisorStrategy.stoppingStrategy();
  }

  @Override
  public void preStart() throws Exception {
    // #manager
    final ActorRef tcpManager = Tcp.get(getContext().getSystem()).manager();
    // #manager
    tcpManager.tell(
        TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 0), 100), getSelf());
  }

  @Override
  public void postRestart(Throwable arg0) throws Exception {
    // do not restart
    getContext().stop(getSelf());
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Bound.class,
            msg -> {
              log.info("listening on [{}]", msg.localAddress());
            })
        .match(
            Tcp.CommandFailed.class,
            failed -> {
              if (failed.cmd() instanceof Bind) {
                log.warning("cannot bind to [{}]", ((Bind) failed.cmd()).localAddress());
                getContext().stop(getSelf());
              } else {
                log.warning("unknown command failed [{}]", failed.cmd());
              }
            })
        .match(
            Connected.class,
            conn -> {
              log.info("received connection from [{}]", conn.remoteAddress());
              final ActorRef connection = getSender();
              final ActorRef handler =
                  getContext()
                      .actorOf(Props.create(handlerClass, connection, conn.remoteAddress()));
              // #echo-manager
              connection.tell(
                  TcpMessage.register(
                      handler, true, // <-- keepOpenOnPeerClosed flag
                      true),
                  getSelf());
              // #echo-manager
            })
        .build();
  }
}
