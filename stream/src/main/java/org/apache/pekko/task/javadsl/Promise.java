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

import org.apache.pekko.Done;
import org.apache.pekko.task.AwaitDef;
import org.apache.pekko.task.CompleteDef;
import org.apache.pekko.task.PromiseDef;

public class Promise<T> {
  private final PromiseDef<T> definition;

  Promise(PromiseDef<T> definition) {
    this.definition = definition;
  }

  public static <T> Task<Promise<T>> make() {
    return Task.succeed(new Promise<>(new PromiseDef<>()));
  }

  public Task<T> await() {
    return new Task<>(new AwaitDef<>(definition));
  }

  /**
   * Completes the promise with the given result, or does nothing if the promise was already
   * completed.
   */
  // FIXME add a Boolean result here, returning if the promise was already completed.
  public Task<Done> succeed(T result) {
    return completeWith(Task.succeed(result));
  }

  public Task<Done> fail(Throwable exception) {
    return completeWith(Task.fail(exception));
  }

  public Task<Done> completeWith(Task<T> task) {
    return new Task<>(new CompleteDef<>(definition, task.definition())).asDone();
  }
}
