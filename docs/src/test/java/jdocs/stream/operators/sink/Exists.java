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

package jdocs.stream.operators.sink;

// #imports

import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.function.Predicate;
import org.apache.pekko.japi.function.Procedure;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;
// #imports

public class Exists {
  private static final ActorSystem system = null;

  private void detectAnomaly() {
    // #exists
    final Source<String, NotUsed> source =
        Source.from(Arrays.asList("Sun is shining", "Unidentified Object", "River is flowing"));

    List<String> anomalies = Collections.singletonList("Unidentified Object");
    Predicate<String> isAnomaly =
        new Predicate<String>() {
          @Override
          public boolean test(String phenomenon) {
            return anomalies.contains(phenomenon);
          }
        };

    CompletionStage<Boolean> result = source.runWith(Sink.exists(isAnomaly), system);

    result.toCompletableFuture().complete(true);
    // #exists
  }
}
