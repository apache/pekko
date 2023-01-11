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

package jdocs.dispatcher;

// #mailbox-implementation-example
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.dispatch.Envelope;
import org.apache.pekko.dispatch.MailboxType;
import org.apache.pekko.dispatch.MessageQueue;
import org.apache.pekko.dispatch.ProducesMessageQueue;
import com.typesafe.config.Config;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import scala.Option;

public class MyUnboundedMailbox
    implements MailboxType, ProducesMessageQueue<MyUnboundedMailbox.MyMessageQueue> {

  // This is the MessageQueue implementation
  public static class MyMessageQueue implements MessageQueue, MyUnboundedMessageQueueSemantics {
    private final Queue<Envelope> queue = new ConcurrentLinkedQueue<Envelope>();

    // these must be implemented; queue used as example
    public void enqueue(ActorRef receiver, Envelope handle) {
      queue.offer(handle);
    }

    public Envelope dequeue() {
      return queue.poll();
    }

    public int numberOfMessages() {
      return queue.size();
    }

    public boolean hasMessages() {
      return !queue.isEmpty();
    }

    public void cleanUp(ActorRef owner, MessageQueue deadLetters) {
      while (!queue.isEmpty()) {
        deadLetters.enqueue(owner, dequeue());
      }
    }
  }

  // This constructor signature must exist, it will be called by Pekko
  public MyUnboundedMailbox(ActorSystem.Settings settings, Config config) {
    // put your initialization code here
  }

  // The create method is called to create the MessageQueue
  public MessageQueue create(Option<ActorRef> owner, Option<ActorSystem> system) {
    return new MyMessageQueue();
  }
}
// #mailbox-implementation-example
