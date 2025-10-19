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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;
import jdocs.AbstractJavaTest;
import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.actor.Props;
import org.apache.pekko.io.Udp;
import org.apache.pekko.testkit.SocketUtil;
import org.apache.pekko.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class JavaUdpMulticastTest extends AbstractJavaTest {

  static ActorSystem system;

  @BeforeClass
  public static void setup() {
    system = ActorSystem.create("JavaUdpMulticastTest");
  }

  @Test
  public void testUdpMulticast() throws Exception {
    new TestKit(system) {
      {
        List<NetworkInterface> ipv6Ifaces = new ArrayList<>();
        for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            interfaces.hasMoreElements(); ) {
          NetworkInterface interf = interfaces.nextElement();
          if (interf.isUp() && interf.supportsMulticast()) {
            for (Enumeration<InetAddress> addresses = interf.getInetAddresses();
                addresses.hasMoreElements(); ) {
              InetAddress address = addresses.nextElement();
              if (address instanceof Inet6Address) {
                ipv6Ifaces.add(interf);
              }
            }
          }
        }
        if (ipv6Ifaces.isEmpty()) {
          system
              .log()
              .info(
                  "JavaUdpMulticastTest skipped since no ipv6 interface supporting multicast could be found");
        } else {
          // lots of problems with choosing the wrong interface for this test depending
          // on the platform (awsdl0 can't be used on OSX, docker[0-9] can't be used in a docker
          // machine etc.)
          // therefore: try hard to find an interface that _does_ work, and only fail if there was
          // any potentially
          // working interfaces but all failed
          for (Iterator<NetworkInterface> interfaceIterator = ipv6Ifaces.iterator();
              interfaceIterator.hasNext(); ) {
            NetworkInterface ipv6Iface = interfaceIterator.next();
            // host assigned link local multicast address
            // https://www.rfc-editor.org/rfc/rfc3307#section-4.3.2
            // generate a random 32 bit multicast address with the high order bit set
            final String randomAddress =
                Long.toHexString(((long) Math.abs(new Random().nextInt())) | (1L << 31))
                    .toUpperCase();
            final StringBuilder groupBuilder = new StringBuilder("FF02:");
            for (int i = 0; i < 2; i += 1) {
              groupBuilder.append(":");
              groupBuilder.append(randomAddress.subSequence(i * 4, i * 4 + 4));
            }
            final String group = groupBuilder.toString();
            final Integer port = SocketUtil.temporaryUdpIpv6Port(ipv6Iface);
            final String msg = "ohi";
            final ActorRef sink = getRef();
            final String iface = ipv6Iface.getName();

            final ActorRef listener =
                system.actorOf(
                    Props.create(JavaUdpMulticast.Listener.class, iface, group, port, sink));

            try {
              expectMsgClass(Udp.Bound.class);
              final ActorRef sender =
                  system.actorOf(
                      Props.create(JavaUdpMulticast.Sender.class, iface, group, port, msg));
              expectMsgEquals(msg);
              // success with one interface is enough
              break;

            } catch (AssertionError ex) {
              if (!interfaceIterator.hasNext()) throw ex;
              else {
                system.log().info("Failed to run test on interface {}", ipv6Iface.getDisplayName());
              }
            } finally {
              // unbind
              system.stop(listener);
            }
          }
        }
      }
    };
  }

  @AfterClass
  public static void tearDown() {
    TestKit.shutdownActorSystem(system);
    system = null;
  }
}
