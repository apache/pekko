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

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.Queue;
import org.apache.pekko.actor.AbstractActor;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.event.Logging;
import org.apache.pekko.event.LoggingAdapter;
import org.apache.pekko.io.Tcp.ConnectionClosed;
import org.apache.pekko.io.Tcp.Event;
import org.apache.pekko.io.Tcp.Received;
import org.apache.pekko.io.TcpMessage;
import org.apache.pekko.util.ByteString;

// #simple-echo-handler
public class SimpleEchoHandler extends AbstractActor {

  final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), getSelf());

  final ActorRef connection;
  final InetSocketAddress remote;

  public static final long maxStored = 100000000;
  public static final long highWatermark = maxStored * 5 / 10;
  public static final long lowWatermark = maxStored * 2 / 10;

  public SimpleEchoHandler(ActorRef connection, InetSocketAddress remote) {
    this.connection = connection;
    this.remote = remote;

    // sign death pact: this actor stops when the connection is closed
    getContext().watch(connection);
  }

  @Override
  public Receive createReceive() {
    return receiveBuilder()
        .match(
            Received.class,
            msg -> {
              final ByteString data = msg.data();
              buffer(data);
              connection.tell(TcpMessage.write(data, ACK), getSelf());
              // now switch behavior to “waiting for acknowledgement”
              getContext().become(buffering(), false);
            })
        .match(
            ConnectionClosed.class,
            msg -> {
              getContext().stop(getSelf());
            })
        .build();
  }

  private Receive buffering() {
    return receiveBuilder()
        .match(
            Received.class,
            msg -> {
              buffer(msg.data());
            })
        .match(
            Event.class,
            msg -> msg == ACK,
            msg -> {
              acknowledge();
            })
        .match(
            ConnectionClosed.class,
            msg -> {
              if (msg.isPeerClosed()) {
                closing = true;
              } else {
                // could also be ErrorClosed, in which case we just give up
                getContext().stop(getSelf());
              }
            })
        .build();
  }

  // #storage-omitted
  public void postStop() {
    log.info("transferred {} bytes from/to [{}]", transferred, remote);
  }

  private long transferred;
  private long stored = 0;
  private Queue<ByteString> storage = new LinkedList<>();

  private boolean suspended = false;
  private boolean closing = false;

  private final Event ACK = new Event() {};

  // #simple-helpers
  protected void buffer(ByteString data) {
    storage.add(data);
    stored += data.size();

    if (stored > maxStored) {
      log.warning("drop connection to [{}] (buffer overrun)", remote);
      getContext().stop(getSelf());

    } else if (stored > highWatermark) {
      log.debug("suspending reading");
      connection.tell(TcpMessage.suspendReading(), getSelf());
      suspended = true;
    }
  }

  protected void acknowledge() {
    final ByteString acked = storage.remove();
    stored -= acked.size();
    transferred += acked.size();

    if (suspended && stored < lowWatermark) {
      log.debug("resuming reading");
      connection.tell(TcpMessage.resumeReading(), getSelf());
      suspended = false;
    }

    if (storage.isEmpty()) {
      if (closing) {
        getContext().stop(getSelf());
      } else {
        getContext().unbecome();
      }
    } else {
      connection.tell(TcpMessage.write(storage.peek(), ACK), getSelf());
    }
  }
  // #simple-helpers
  // #storage-omitted
}
// #simple-echo-handler
