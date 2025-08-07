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

import org.apache.pekko.task.FiberDef;
import org.apache.pekko.task.AwaitDef;
import org.apache.pekko.task.InterruptDef;
import org.apache.pekko.Done;

/**
 * A fiber represents the ongoing execution of a Task, eventually resulting in a value T or failing
 * with an exception.
 */
public class Fiber<T> {
  private final FiberDef<T> definition;

  public Fiber(FiberDef<T> definition) {
    this.definition = definition;
  }

  /** Returns a Task that will complete when this fiber does. */
  public Task<T> join() {
    return new Task<>(definition.join());
  }

  /**
   * Returns a Task that will interrupt this fiber, causing its execution to stop. If the fiber has
   * already completed, the task will complete with the fiber's result. Otherwise, the task will
   * complete with InterruptedException.
   */
  public Task<T> interruptAndGet() {
    return new Task<>(definition.interruptAndGet());
  }

  /**
   * Returns a Task that will interrupt this fiber, causing its execution to stop. The task will
   * complete once interruption has finished.
   */
  public Task<Done> interrupt() {
    return new Task<>(definition.interrupt()).asDone();
  }

  /**
   * Returns a Resource wrapping this running fiber, which will interrupt the fiber when the
   * resource's lifetime ends.
   */
  public Resource<Fiber<T>> toResource() {
    return Resource.acquireRelease(Task.succeed(this), f -> f.interrupt());
  }
}
