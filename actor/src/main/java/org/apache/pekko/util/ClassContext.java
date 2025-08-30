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

package org.apache.pekko.util;

import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.apache.pekko.annotation.InternalApi;

/** INTERNAL API */
@InternalApi
public final class ClassContext {
  private ClassContext() {
    throw new UnsupportedOperationException("Cannot instantiate utility class");
  }

  private static final Set<StackWalker.Option> OPTIONS =
      Set.of(StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_HIDDEN_FRAMES);

  private static final Function<Stream<StackWalker.StackFrame>, Class<?>[]> CLASS_STACK_WALKER =
      frames -> frames.map(StackWalker.StackFrame::getDeclaringClass).toArray(Class<?>[]::new);

  public static Class<?>[] getClassStack() {
    return StackWalker.getInstance(OPTIONS).walk(CLASS_STACK_WALKER);
  }
}
