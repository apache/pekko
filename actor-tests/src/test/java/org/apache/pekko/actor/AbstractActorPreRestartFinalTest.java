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

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class AbstractActorPreRestartFinalTest {

  @Test
  public void preRestartWithScalaOptionShouldBeFinal() throws NoSuchMethodException {
    var method =
        AbstractActor.class.getDeclaredMethod("preRestart", Throwable.class, scala.Option.class);
    assertTrue(
        Modifier.isFinal(method.getModifiers()),
        "preRestart(Throwable, Option) should be final in AbstractActor");
  }

  @Test
  public void preRestartWithJavaOptionalShouldNotBeFinal() throws NoSuchMethodException {
    var method =
        AbstractActor.class.getDeclaredMethod("preRestart", Throwable.class, Optional.class);
    assertFalse(
        Modifier.isFinal(method.getModifiers()),
        "preRestart(Throwable, Optional) should NOT be final in AbstractActor");
  }

  @Test
  public void untypedAbstractActorShouldInheritFinalPreRestart() throws NoSuchMethodException {
    var method =
        UntypedAbstractActor.class.getMethod("preRestart", Throwable.class, scala.Option.class);
    assertTrue(
        Modifier.isFinal(method.getModifiers()),
        "preRestart(Throwable, Option) should be final in UntypedAbstractActor");
  }

  @Test
  public void abstractActorWithStashShouldExtendStashSupport() {
    assertTrue(
        StashSupport.class.isAssignableFrom(AbstractActorWithStash.class),
        "AbstractActorWithStash should implement StashSupport");
  }

  @Test
  public void abstractActorWithUnboundedStashShouldExtendStashSupport() {
    assertTrue(
        StashSupport.class.isAssignableFrom(AbstractActorWithUnboundedStash.class),
        "AbstractActorWithUnboundedStash should implement StashSupport");
  }

  @Test
  public void abstractActorWithUnrestrictedStashShouldExtendStashSupport() {
    assertTrue(
        StashSupport.class.isAssignableFrom(AbstractActorWithUnrestrictedStash.class),
        "AbstractActorWithUnrestrictedStash should implement StashSupport");
  }
}
