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

package jdocs.actor.io.dns;

import static org.apache.pekko.pattern.Patterns.ask;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.io.Dns;
import org.apache.pekko.io.dns.DnsProtocol;
import scala.Option;

public class DnsCompileOnlyDocTest {
  public static void example() {
    ActorSystem system = ActorSystem.create();

    ActorRef actorRef = null;
    final Duration timeout = Duration.ofMillis(1000L);

    // #resolve
    Option<DnsProtocol.Resolved> initial =
        Dns.get(system)
            .cache()
            .resolve(
                new DnsProtocol.Resolve("google.com", DnsProtocol.ipRequestType()),
                system,
                actorRef);
    Option<DnsProtocol.Resolved> cached =
        Dns.get(system)
            .cache()
            .cached(new DnsProtocol.Resolve("google.com", DnsProtocol.ipRequestType()));
    // #resolve

    {
      // #actor-api-inet-address
      final ActorRef dnsManager = Dns.get(system).manager();
      CompletionStage<Object> resolved =
          ask(
              dnsManager,
              new DnsProtocol.Resolve("google.com", DnsProtocol.ipRequestType()),
              timeout);
      // #actor-api-inet-address

    }

    {
      // #actor-api-async
      final ActorRef dnsManager = Dns.get(system).manager();
      CompletionStage<Object> resolved =
          ask(dnsManager, DnsProtocol.resolve("google.com"), timeout);
      // #actor-api-async
    }

    {
      // #srv
      final ActorRef dnsManager = Dns.get(system).manager();
      CompletionStage<Object> resolved =
          ask(dnsManager, DnsProtocol.resolve("google.com", DnsProtocol.srvRequestType()), timeout);
      // #srv
    }
  }
}
