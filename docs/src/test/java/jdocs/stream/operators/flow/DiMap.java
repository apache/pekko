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

package jdocs.stream.operators.flow;

// #imports
import org.apache.pekko.NotUsed;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Flow;
import org.apache.pekko.stream.javadsl.Source;

import java.util.Arrays;
// #imports

public class DiMap {
  private static final ActorSystem system = null;

  private void demoDiMapUsage() {
    // #dimap
    final Source<String, NotUsed> source = Source.from(Arrays.asList("1", "2", "3"));
    final Flow<Integer, Integer, NotUsed> flow = Flow.<Integer>create().map(elem -> elem * 2);
    source
        .via(flow.dimap(Integer::parseInt, String::valueOf))
        .runForeach(System.out::println, system);
    // expected prints:
    // 2
    // 4
    // 6
    // #dimap
  }
}
