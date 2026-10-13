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

/**
 * Compile-only guard for Java-facing base classes that mix in more than one Scala trait.
 *
 * <p>Scala 3 emits the mixin forwarder for a member implemented by two mixed-in traits as an
 * ACC_BRIDGE/ACC_SYNTHETIC method. javac ignores synthetic and bridge members when resolving
 * inherited members, so it falls back to the interface defaults and rejects the subclass with
 * "inherits unrelated defaults" (see #3474). Merely declaring a Java subclass is enough to catch
 * that, since it is a compile error.
 *
 * <p>The classes below have no other Java subclass in the build under JDK 17, so without this file
 * they carry no Java compilation coverage at all.
 */
public final class JavaSubclassCompilationCheck {

  private JavaSubclassCompilationCheck() {}

  abstract static class LoggingActor extends UntypedAbstractLoggingActor {}

  // The `UntypedAbstractActor*Stash` classes are otherwise only subclassed under
  // `src/test/java-jdk21-only`, which is compiled by the separate `TestJdk21` configuration.
  abstract static class ActorWithStash extends UntypedAbstractActorWithStash {}

  abstract static class ActorWithUnboundedStash extends UntypedAbstractActorWithUnboundedStash {}

  abstract static class ActorWithUnrestrictedStash
      extends UntypedAbstractActorWithUnrestrictedStash {}
}
