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

package org.apache.pekko.persistence.fsm;

/**
 * Compile-only guard for Java-facing base classes that mix in more than one Scala trait.
 *
 * <p>See {@code org.apache.pekko.actor.JavaSubclassCompilationCheck} for why declaring the subclass
 * is the whole test. {@link AbstractPersistentLoggingFSM} has no other Java subclass in the build.
 *
 * <p>The class under guard is deprecated, so the subclass is marked deprecated too: javac's test
 * configuration runs with `-Werror`, and a use of deprecated API inside a deprecated element does
 * not warn. This mirrors {@code AbstractPersistentFSMTest}.
 */
public final class JavaSubclassCompilationCheck {

  private JavaSubclassCompilationCheck() {}

  @Deprecated
  abstract static class PersistentLoggingFSM
      extends AbstractPersistentLoggingFSM<PersistentFSM.FSMState, String, String> {}
}
