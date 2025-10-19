/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.io;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.Props;
import org.apache.pekko.io.Inet;
import org.apache.pekko.io.Tcp;
import org.apache.pekko.io.TcpMessage;
import org.apache.pekko.util.ByteString;

/** Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com> */
public class JavaReadBackPressure {

  public static class Listener extends AbstractActor {
    ActorRef tcp;
    ActorRef listener;

    @Override
    // #pull-accepting
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Tcp.Bound.class,
              x -> {
                listener = getSender();
                // Accept connections one by one
                listener.tell(TcpMessage.resumeAccepting(1), getSelf());
              })
          .match(
              Tcp.Connected.class,
              x -> {
                ActorRef handler = getContext().actorOf(Props.create(PullEcho.class, getSender()));
                getSender().tell(TcpMessage.register(handler), getSelf());
                // Resume accepting connections
                listener.tell(TcpMessage.resumeAccepting(1), getSelf());
              })
          .build();
    }
    // #pull-accepting

    @Override
    public void preStart() throws Exception {
      // #pull-mode-bind
      tcp = Tcp.get(getContext().getSystem()).manager();
      final List<Inet.SocketOption> options = new ArrayList<Inet.SocketOption>();
      tcp.tell(
          TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 0), 100, options, true),
          getSelf());
      // #pull-mode-bind
    }

    private void demonstrateConnect() {
      // #pull-mode-connect
      final List<Inet.SocketOption> options = new ArrayList<Inet.SocketOption>();
      Duration timeout = null;
      tcp.tell(
          TcpMessage.connect(
              new InetSocketAddress("localhost", 3000), null, options, timeout, true),
          getSelf());
      // #pull-mode-connect
    }
  }

  public static class Ack implements Tcp.Event {}

  public static class PullEcho extends AbstractActor {
    final ActorRef connection;

    public PullEcho(ActorRef connection) {
      this.connection = connection;
    }

    // #pull-reading-echo
    @Override
    public void preStart() throws Exception {
      connection.tell(TcpMessage.resumeReading(), getSelf());
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Tcp.Received.class,
              message -> {
                ByteString data = message.data();
                connection.tell(TcpMessage.write(data, new Ack()), getSelf());
              })
          .match(
              Ack.class,
              message -> {
                connection.tell(TcpMessage.resumeReading(), getSelf());
              })
          .build();
    }
    // #pull-reading-echo
  }
}
