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

package org.apache.pekko.actor;

import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.apache.pekko.testkit.TestProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Subclassing {@link AbstractFSMWithStash} from Java has to keep compiling: both {@code FSM} and
 * {@code UnrestrictedStash} implement {@code postStop}, and javac rejects a subclass unless the
 * Scala base class carries a real (non-synthetic) override for it.
 */
@SuppressWarnings("unchecked")
public class AbstractFSMWithStashActorTest {

  public static class MyFSM extends AbstractFSMWithStash<String, String> {

    private final ActorRef probe;

    MyFSM(ActorRef probe) {
      this.probe = probe;
      startWith("start", "data");
      when(
          "start",
          matchEvent(
              String.class,
              (event, data) -> {
                if ("go".equals(event)) {
                  unstashAll();
                  return goTo("next");
                } else {
                  stash();
                  return stay();
                }
              }));
      when(
          "next",
          matchEvent(
              String.class,
              (event, data) -> {
                probe.tell(event, getSelf());
                return stay();
              }));
      initialize();
    }
  }

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource(
          "AbstractFSMWithStashActorTest", PekkoSpec.testConf());

  private final ActorSystem system = actorSystemResource.getSystem();

  @Test
  public void canCreateFSMWithStash() {
    TestProbe probe = new TestProbe(system);

    ActorRef ref = system.actorOf(Props.create(MyFSM.class, probe.ref()));
    ref.tell("work", ActorRef.noSender());
    ref.tell("go", ActorRef.noSender());

    probe.expectMsg("work");
  }
}
