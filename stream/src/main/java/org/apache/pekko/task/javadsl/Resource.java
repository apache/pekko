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

package org.apache.pekko.task.javadsl;

import org.apache.pekko.japi.function.Function;
import org.apache.pekko.task.AbstractTask;

import static org.apache.pekko.task.javadsl.Task.*;

/**
 * A Resource represents a value that can be created, but needs to be cleaned up after use. The use
 * of Resource guarantees that values aren't leaked even in case of failure or interrupted fibers.
 */
public class Resource<T> {
  /**
   * Creates a Resource that creates a value using [acquire], invoking [release] when such value
   * needs to be cleaned up.
   */
  public static <T> Resource<T> acquireRelease(
      Task<T> acquire, Function<T, ? extends AbstractTask<?>> release) {
    return new Resource<>(acquire, release);
  }

  private final Task<T> create;
  private final Function<T, ? extends AbstractTask<?>> destroy;

  private Resource(Task<T> create, Function<T, ? extends AbstractTask<?>> destroy) {
    this.create = create;
    this.destroy = destroy;
  }

  /**
   * Returns a Task that can safely use this resource in the given function, guaranteeing cleanup
   * even if the function fails or the current fiber is interrupted.
   */
  public <U> Task<U> use(Function<T, ? extends AbstractTask<U>> fn) {
    return Task.<U>uninterruptableMask(
        restore ->
            create.flatMap(
                t ->
                    runTask(() -> task(restore.<U>apply(task(fn.apply(t)))))
                        .onComplete((r, x) -> runTask(() -> task(destroy.apply(t))))));
  }
}
