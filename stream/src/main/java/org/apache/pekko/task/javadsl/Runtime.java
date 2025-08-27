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

import org.apache.pekko.stream.Materializer;
import org.apache.pekko.task.ClockDef;
import org.apache.pekko.task.AbstractRuntime;
import org.apache.pekko.task.AbstractTask;
import org.apache.pekko.task.FiberRuntime;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.time.Duration;

public class Runtime extends AbstractRuntime {
  public static Runtime create(Materializer materializer) {
    return create(materializer, ClockDef.system());
  }

  public static Runtime create(Materializer materializer, ClockDef clock) {
    return new Runtime(materializer, clock);
  }

  private Runtime(Materializer materializer, ClockDef clock) {
    super(materializer, clock);
  }

  /** Runs the task, and returns a CompletableFuture that will complete with the task's result. */
  public <T> CompletableFuture<T> runAsync(AbstractTask<T> task) {
    CompletableFuture<T> fut = new CompletableFuture<>();
    run(
        new FiberRuntime<T>(),
        task.definition(),
        res -> {
          if (res.isSuccess()) {
            fut.complete(res.get());
          } else {
            fut.completeExceptionally(res.failed().get());
          }
          return null;
        });
    return fut;
  }

  /**
   * Runs the task, and blocks the current thread indefinitely. If you want a timeout, use this
   * method with Task.timeout(). If the task fails, its failing exception is thrown (not wrapped in
   * an ExecptionException). If the task was interrupted, InterruptedException is thrown.
   */
  public <T> T run(AbstractTask<T> task) throws Throwable {
    try {
      return runAsync(task).get();
    } catch (ExecutionException x) {
      throw x.getCause();
    }
  }
}
