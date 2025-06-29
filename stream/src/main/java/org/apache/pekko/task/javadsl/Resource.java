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
import org.apache.pekko.japi.function.Function2;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.Done;
import org.apache.pekko.task.AbstractTask;
import org.apache.pekko.task.AbstractResource;
import org.apache.pekko.task.ResourceDef;

import static org.apache.pekko.task.javadsl.Task.task;
import static org.apache.pekko.task.javadsl.Task.runTask;

/**
 * A Resource represents a value that can be created, but needs to be cleaned up after use. The use
 * of Resource guarantees that values aren't leaked even in case of failure or interrupted fibers.
 */
public class Resource<T> implements AbstractResource<T> {
  /**
   * Creates a Resource that creates a value using [acquire], invoking [release] when such value
   * needs to be cleaned up. It is important that only the Task returned by [release] performs any
   * cleanup, not the lambda itself.
   */
  public static <T> Resource<T> acquireRelease(
      Task<T> acquire, Function<T, ? extends AbstractTask<?>> release) {
    return new Resource<>(
        ResourceDef.acquireRelease(
            acquire.definition(), release.andThen(t -> t.definition()).toScala()));
  }

  public static <T extends AutoCloseable> Resource<T> autoCloseable(Task<T> acquire) {
    return acquireRelease(acquire, t -> Task.run(() -> t.close()));
  }

  public static <T> Resource<T> succeedTask(Task<T> task) {
    return new Resource<>(ResourceDef.succeedTask(() -> task.definition()));
  }

  public static <T> Resource<T> succeed(T value) {
    return new Resource<>(ResourceDef.succeed(() -> value));
  }

  private final ResourceDef<T> definition;

  /**
   * @param create Task that returns the value that this resource governs, and a task can clean up
   *     that value.
   */
  Resource(ResourceDef<T> definition) {
    this.definition = definition;
  }

  public ResourceDef<T> definition() {
    return definition;
  }

  public <U> Resource<U> map(Function<? super T, ? extends U> fn) {
    return new Resource<>(definition.map(fn.toScala()));
  }

  public <U> Resource<U> mapTask(Function<? super T, ? extends AbstractTask<U>> fn) {
    return new Resource<>(definition.mapTask(fn.andThen(t -> t.definition()).toScala()));
  }

  public <U> Resource<U> flatMap(Function<? super T, Resource<U>> fn) {
    return new Resource<>(definition.flatMap(fn.andThen(t -> t.definition()).toScala()));
  }

  public <U, R> Resource<R> zip(
      Resource<U> that, Function2<? super T, ? super U, ? extends R> combine) {
    return flatMap(t -> that.map(u -> combine.apply(t, u)));
  }

  /**
   * Returns a Resource that creates this resource's value in a background fiber, making sure to
   * interrupt that fiber when closing the resource.
   */
  public Resource<Fiber<T>> fork() {
    return new Resource<>(definition.fork().map(Fiber::new));
  }

  /**
   * Returns a Task that can safely use this resource in the given function, guaranteeing cleanup
   * even if the function fails or the current fiber is interrupted.
   */
  public <U> Task<U> use(Function<T, ? extends AbstractTask<U>> fn) {
    return new Task<>(definition.use(fn.andThen(t -> t.definition()).toScala()));
  }
}
