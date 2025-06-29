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

import java.time.Duration;
import java.time.Instant;

import org.apache.pekko.task.GetRuntimeDef$;
import org.apache.pekko.task.ClockDef$;
import org.apache.pekko.task.TaskDef;
import org.apache.pekko.Done;

public class Clock {
  public static final Task<Long> nanoTime = new Task<>(ClockDef$.MODULE$.nanoTime());
  public static final Task<Instant> now = new Task<>(ClockDef$.MODULE$.now());

  public static Task<Done> sleep(Duration duration) {
    return new Task<>(ClockDef$.MODULE$.sleep(duration)).asDone();
  }
}
