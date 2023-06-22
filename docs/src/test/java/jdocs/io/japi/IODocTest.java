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

import org.apache.pekko.testkit.PekkoJUnitActorSystemResource;
import jdocs.AbstractJavaTest;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.ClassRule;
import org.junit.Test;

// #imports
import java.net.InetSocketAddress;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.io.Tcp;
import org.apache.pekko.io.Tcp.Bound;
import org.apache.pekko.io.Tcp.CommandFailed;
import org.apache.pekko.io.Tcp.Connected;
import org.apache.pekko.io.Tcp.ConnectionClosed;
import org.apache.pekko.io.Tcp.Received;
import org.apache.pekko.io.TcpMessage;
import org.apache.pekko.util.ByteString;
// #imports

import org.apache.pekko.testkit.PekkoSpec;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IODocTest extends AbstractJavaTest {

  public
  // #server
  static class Server extends AbstractActor {

    final ActorRef manager;

    public Server(ActorRef manager) {
      this.manager = manager;
    }

    public static Props props(ActorRef manager) {
      return Props.create(Server.class, manager);
    }

    @Override
    public void preStart() throws Exception {
      final ActorRef tcp = Tcp.get(getContext().getSystem()).manager();
      tcp.tell(TcpMessage.bind(getSelf(), new InetSocketAddress("localhost", 0), 100), getSelf());
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Bound.class,
              msg -> {
                manager.tell(msg, getSelf());
              })
          .match(
              CommandFailed.class,
              msg -> {
                getContext().stop(getSelf());
              })
          .match(
              Connected.class,
              conn -> {
                manager.tell(conn, getSelf());
                final ActorRef handler =
                    getContext().actorOf(Props.create(SimplisticHandler.class));
                getSender().tell(TcpMessage.register(handler), getSelf());
              })
          .build();
    }
  }
  // #server

  public
  // #simplistic-handler
  static class SimplisticHandler extends AbstractActor {
    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              Received.class,
              msg -> {
                final ByteString data = msg.data();
                System.out.println(data);
                getSender().tell(TcpMessage.write(data), getSelf());
              })
          .match(
              ConnectionClosed.class,
              msg -> {
                getContext().stop(getSelf());
              })
          .build();
    }
  }
  // #simplistic-handler

  public
  // #client
  static class Client extends AbstractActor {

    final InetSocketAddress remote;
    final ActorRef listener;

    public static Props props(InetSocketAddress remote, ActorRef listener) {
      return Props.create(Client.class, remote, listener);
    }

    public Client(InetSocketAddress remote, ActorRef listener) {
      this.remote = remote;
      this.listener = listener;

      final ActorRef tcp = Tcp.get(getContext().getSystem()).manager();
      tcp.tell(TcpMessage.connect(remote), getSelf());
    }

    @Override
    public Receive createReceive() {
      return receiveBuilder()
          .match(
              CommandFailed.class,
              msg -> {
                listener.tell("failed", getSelf());
                getContext().stop(getSelf());
              })
          .match(
              Connected.class,
              msg -> {
                listener.tell(msg, getSelf());
                getSender().tell(TcpMessage.register(getSelf()), getSelf());
                getContext().become(connected(getSender()));
              })
          .build();
    }

    private Receive connected(final ActorRef connection) {
      return receiveBuilder()
          .match(
              ByteString.class,
              msg -> {
                connection.tell(TcpMessage.write((ByteString) msg), getSelf());
              })
          .match(
              CommandFailed.class,
              msg -> {
                // OS kernel socket buffer was full
              })
          .match(
              Received.class,
              msg -> {
                listener.tell(msg.data(), getSelf());
              })
          .matchEquals(
              "close",
              msg -> {
                connection.tell(TcpMessage.close(), getSelf());
              })
          .match(
              ConnectionClosed.class,
              msg -> {
                getContext().stop(getSelf());
              })
          .build();
    }
  }
  // #client

  @ClassRule
  public static PekkoJUnitActorSystemResource actorSystemResource =
      new PekkoJUnitActorSystemResource("IODocTest", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  @Test
  public void testConnection() {
    new TestKit(system) {
      {
        @SuppressWarnings("unused")
        final ActorRef server = system.actorOf(Server.props(getRef()), "server1");
        final InetSocketAddress listen = expectMsgClass(Bound.class).localAddress();
        final ActorRef client = system.actorOf(Client.props(listen, getRef()), "client1");

        final Connected c1 = expectMsgClass(Connected.class);
        final Connected c2 = expectMsgClass(Connected.class);
        assertTrue(c1.localAddress().equals(c2.remoteAddress()));
        assertTrue(c2.localAddress().equals(c1.remoteAddress()));

        client.tell(ByteString.fromString("hello"), getRef());
        final ByteString reply = expectMsgClass(ByteString.class);
        assertEquals("hello", reply.utf8String());

        watch(client);
        client.tell("close", getRef());
        expectTerminated(client);
      }
    };
  }
}
