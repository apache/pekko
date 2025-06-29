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

import java.util.concurrent.CompletionStage;

import org.apache.pekko.task.RunningGraph;
import org.apache.pekko.Done;
import org.apache.pekko.stream.KillSwitch;

public class RunningGraphs {
  /**
   * Creates a RunningGraph from the given CompletionStage, using shutdown on the given ks to
   * interrupt
   */
  public static <T> RunningGraph<T> withShutdown(KillSwitch ks, CompletionStage<T> cs) {
    return RunningGraph.create(Task.completeUninterruptable(cs), Task.run(() -> ks.shutdown()));
  }

  /**
   * Creates a RunningGraph from the given CompletionStage, using abort on the given ks to interrupt
   */
  public static <T> RunningGraph<T> withAbort(KillSwitch ks, CompletionStage<T> cs) {
    return RunningGraph.create(
        Task.completeUninterruptable(cs),
        Task.run(() -> ks.abort(new InterruptedException("Task was interrupted"))));
  }
}
