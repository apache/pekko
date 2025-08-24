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

package jdocs.io;

// #imports
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.io.Inet;
import org.apache.pekko.io.Tcp;
import org.apache.pekko.io.TcpMessage;
import org.apache.pekko.io.TcpSO;
import org.apache.pekko.util.ByteString;

// #imports

public class IODocTest {

  public static class Demo extends AbstractActor {
    ActorRef connectionActor = null;
    ActorRef listener = getSelf();

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .matchEquals(
              "connect",
              msg -> {
                // #manager
                final ActorRef tcp = Tcp.get(system).manager();
                // #manager
                // #connect
                final InetSocketAddress remoteAddr = new InetSocketAddress("127.0.0.1", 12345);
                tcp.tell(TcpMessage.connect(remoteAddr), getSelf());
                // #connect
                // #connect-with-options
                final InetSocketAddress localAddr = new InetSocketAddress("127.0.0.1", 1234);
                final List<Inet.SocketOption> options = new ArrayList<Inet.SocketOption>();
                options.add(TcpSO.keepAlive(true));
                Duration timeout = null;
                tcp.tell(
                    TcpMessage.connect(remoteAddr, localAddr, options, timeout, false), getSelf());
                // #connect-with-options
              })
          // #connected
          .match(
              Tcp.Connected.class,
              conn -> {
                connectionActor = getSender();
                connectionActor.tell(TcpMessage.register(listener), getSelf());
              })
          // #connected
          // #received
          .match(
              Tcp.Received.class,
              recv -> {
                final ByteString data = recv.data();
                // and do something with the received data ...
              })
          .match(
              Tcp.CommandFailed.class,
              failed -> {
                final Tcp.Command command = failed.cmd();
                // react to failed connect, bind, write, etc.
              })
          .match(
              Tcp.ConnectionClosed.class,
              closed -> {
                if (closed.isAborted()) {
                  // handle close reasons like this
                }
              })
          // #received
          .matchEquals(
              "bind",
              msg -> {
                final ActorRef handler = getSelf();
                // #bind
                final ActorRef tcp = Tcp.get(system).manager();
                final InetSocketAddress localAddr = new InetSocketAddress("127.0.0.1", 1234);
                final List<Inet.SocketOption> options = new ArrayList<Inet.SocketOption>();
                options.add(TcpSO.reuseAddress(true));
                tcp.tell(TcpMessage.bind(handler, localAddr, 10, options, false), getSelf());
                // #bind
              })
          .build();
    }
  }

  static ActorSystem system;

  // This is currently only a compilation test, nothing is run
}
