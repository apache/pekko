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

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import scala.Option;
import scala.concurrent.Future;

/**
 * These tests are here to ensure that methods from {@link org.apache.pekko.util.FutureConverters},
 * {@link org.apache.pekko.util.JavaDurationConverters} and {@link
 * org.apache.pekko.util.OptionConverters} for use within Java can be compiled from with Java
 * sources. This is because methods marked with the Scala 3 inline keyword cannot be called from
 * within Java (see https://github.com/lampepfl/dotty/issues/19346)
 */
public class JavaConverterScala3InlineTest {
  public void compileTest() {
    OptionConverters.toScala(Optional.empty());
    OptionConverters.toScala(OptionalDouble.of(0));
    OptionConverters.toScala(OptionalInt.of(0));
    OptionConverters.toScala(OptionalLong.of(0));
    OptionConverters.toJava(Option.empty());

    FutureConverters.asJava(Future.successful(""));
    FutureConverters.asScala(CompletableFuture.completedFuture(""));

    JavaDurationConverters.asFiniteDuration(Duration.ofMillis(0));
  }
}
