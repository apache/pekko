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

package org.apache.pekko.stream.javadsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.japi.pf.PFBuilder;
import org.apache.pekko.stream.StreamTestJupiter;
import org.apache.pekko.testkit.PekkoJUnitJupiterActorSystemResource;
import org.apache.pekko.testkit.PekkoSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SourceWithContextTest extends StreamTestJupiter {

  public SourceWithContextTest() {
    super(actorSystemResource);
  }

  @RegisterExtension
  static PekkoJUnitJupiterActorSystemResource actorSystemResource =
      new PekkoJUnitJupiterActorSystemResource("SourceWithContextTest", PekkoSpec.testConf());

  @Test
  public void mustPassContextThroughAdditionalFilteringOperators() throws Exception {
    List<Pair<Object, String>> collectInput =
        List.of(
            new Pair<Object, String>(1, "one"),
            new Pair<Object, String>(2, "two"),
            new Pair<Object, String>("three", "three-string"),
            new Pair<Object, String>(3, "three"));

    List<Pair<Integer, String>> collectResult =
        SourceWithContext.fromPairs(Source.from(collectInput))
            .collectType(Integer.class)
            .collectWhile(
                PFBuilder.<Integer, Integer>create()
                    .match(Integer.class, value -> value < 3, value -> value * 10)
                    .build())
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(10, "one"), new Pair<>(20, "two")), collectResult);

    List<Pair<Integer, String>> collectFirstResult =
        SourceWithContext.fromPairs(Source.from(collectInput))
            .collectFirst(
                PFBuilder.<Object, Integer>create()
                    .match(Integer.class, value -> value > 1, value -> value * 10)
                    .build())
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(20, "two")), collectFirstResult);

    List<Pair<Integer, String>> mapOptionInput =
        List.of(
            new Pair<>(0, "zero"),
            new Pair<>(1, "one"),
            new Pair<>(2, "two"),
            new Pair<>(3, "three"));

    List<Pair<Integer, String>> mapOptionResult =
        SourceWithContext.fromPairs(Source.from(mapOptionInput))
            .mapOption(value -> value == 0 ? Optional.empty() : Optional.of(value * 10))
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(
        List.of(new Pair<>(10, "one"), new Pair<>(20, "two"), new Pair<>(30, "three")),
        mapOptionResult);
  }

  @Test
  public void mustPassContextThroughTruncatingOperators() throws Exception {
    List<Pair<Integer, String>> input =
        List.of(
            new Pair<>(0, "zero"),
            new Pair<>(1, "one"),
            new Pair<>(1, "one-duplicate"),
            new Pair<>(2, "two"),
            new Pair<>(3, "three"),
            new Pair<>(4, "four"));

    List<Pair<Integer, String>> result =
        SourceWithContext.fromPairs(Source.from(input))
            .drop(1)
            .dropRepeated()
            .dropWhile(value -> value < 2)
            .takeUntil(value -> value == 3)
            .takeWithin(Duration.ofDays(1))
            .take(2)
            .limit(2)
            .limitWeighted(2, value -> 1L)
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(2, "two"), new Pair<>(3, "three")), result);

    List<Pair<Integer, String>> inclusiveResult =
        SourceWithContext.fromPairs(
                Source.from(
                    List.of(new Pair<>(1, "one"), new Pair<>(2, "two"), new Pair<>(3, "three"))))
            .takeWhile(value -> value < 2, true)
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(1, "one"), new Pair<>(2, "two")), inclusiveResult);

    List<Pair<Integer, String>> customPredicateResult =
        SourceWithContext.fromPairs(
                Source.from(
                    List.of(new Pair<>(1, "one"), new Pair<>(3, "three"), new Pair<>(4, "four"))))
            .dropRepeated((left, right) -> left % 2 == right % 2)
            .takeWhile(value -> value < 3)
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(1, "one")), customPredicateResult);

    List<Pair<Integer, String>> timedResult =
        SourceWithContext.fromPairs(
                Source.single(new Pair<>(1, "one")).initialDelay(Duration.ofMillis(50)))
            .dropWithin(Duration.ofMillis(10))
            .takeWithin(Duration.ofDays(1))
            .runWith(Sink.seq(), system)
            .toCompletableFuture()
            .get(3, TimeUnit.SECONDS);

    assertEquals(List.of(new Pair<>(1, "one")), timedResult);
  }
}
