/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jdocs.org.apache.pekko.actor.testkit.typed.javadsl;

import org.apache.pekko.actor.testkit.typed.annotations.JUnit5TestKit;
import org.apache.pekko.actor.testkit.typed.javadsl.*;
import org.apache.pekko.actor.testkit.typed.javadsl.JUnit5TestKitBuilder;
import org.apache.pekko.actor.typed.ActorRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static jdocs.org.apache.pekko.actor.testkit.typed.javadsl.AsyncTestingExampleTest.Echo;

// test code copied from LogCapturingExampleTest.java

@DisplayName("JUnit5 log capturing")
@ExtendWith(TestKitJUnit5Extension.class)
@ExtendWith(LogCapturingExtension.class)
class LogCapturingExtensionExampleTest {

  @JUnit5TestKit
  public ActorTestKit testKit = new JUnit5TestKitBuilder().build();

  @Test
  void testSomething() {
    ActorRef<Echo.Ping> pinger = testKit.spawn(Echo.create(), "ping");
    TestProbe<Echo.Pong> probe = testKit.createTestProbe();
    pinger.tell(new Echo.Ping("hello", probe.ref()));
    probe.expectMessage(new Echo.Pong("hello"));
  }

  @Test
  void testSomething2() {
    ActorRef<Echo.Ping> pinger = testKit.spawn(Echo.create(), "ping");
    TestProbe<Echo.Pong> probe = testKit.createTestProbe();
    pinger.tell(new Echo.Ping("hello", probe.ref()));
    probe.expectMessage(new Echo.Pong("hello"));
  }
}
// #log-capturing-junit5
