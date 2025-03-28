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
import org.apache.pekko.task.JoinDef;
import org.apache.pekko.task.InterruptDef;
import org.apache.pekko.Done;

/**
 * A fiber represents the ongoing execution of a Task, eventually resulting in a value T or failing
 * with an exception.
 */
public class Fiber<T> {
  private final FiberDef<T> impl;

  public Fiber(FiberDef<T> impl) {
    this.impl = impl;
  }

  /** Returns a Task that will complete when this fiber does. */
  public Task<T> join() {
    return new Task<>(new JoinDef<>(impl));
  }

  /**
   * Returns a Task that will interrupt this fiber, causing its execution to stop. The task will
   * complete with Done when this fiber has fully stopped.
   */
  public Task<Done> interrupt() {
    return new Task<>(new InterruptDef<>(impl)).asDone();
  }
}
