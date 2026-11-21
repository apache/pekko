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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.GatherCollector;
import org.apache.pekko.stream.javadsl.Gatherer;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public class Gather {

  static final ActorSystem system = null;

  static void zipWithIndex() {
    // #zipWithIndex
    Source.from(Arrays.asList("A", "B", "C", "D"))
        .gather(
            () ->
                new Gatherer<String, String>() {
                  private long index = 0L;

                  @Override
                  public void apply(String elem, GatherCollector<String> collector) {
                    collector.push("(" + elem + "," + index + ")");
                    index += 1;
                  }
                })
        .runWith(Sink.foreach(System.out::println), system);
    // prints
    // (A,0)
    // (B,1)
    // (C,2)
    // (D,3)
    // #zipWithIndex
  }

  static void bufferUntilChanged() {
    // #bufferUntilChanged
    Source.from(Arrays.asList("A", "B", "B", "C", "C", "C", "D"))
        .gather(
            () ->
                new Gatherer<String, List<String>>() {
                  private final List<String> buffer = new ArrayList<>();

                  @Override
                  public void apply(String elem, GatherCollector<List<String>> collector) {
                    if (!buffer.isEmpty() && !buffer.get(0).equals(elem)) {
                      collector.push(new ArrayList<>(buffer));
                      buffer.clear();
                    }
                    buffer.add(elem);
                  }

                  @Override
                  public void onComplete(GatherCollector<List<String>> collector) {
                    if (!buffer.isEmpty()) {
                      collector.push(new ArrayList<>(buffer));
                    }
                  }
                })
        .runWith(Sink.foreach(System.out::println), system);
    // prints
    // [A]
    // [B, B]
    // [C, C, C]
    // [D]
    // #bufferUntilChanged
  }

  static void distinctUntilChanged() {
    // #distinctUntilChanged
    Source.from(Arrays.asList("A", "B", "B", "C", "C", "C", "D"))
        .gather(
            () ->
                new Gatherer<String, String>() {
                  private String lastElement = null;

                  @Override
                  public void apply(String elem, GatherCollector<String> collector) {
                    if (!elem.equals(lastElement)) {
                      lastElement = elem;
                      collector.push(elem);
                    }
                  }
                })
        .runWith(Sink.foreach(System.out::println), system);
    // prints
    // A
    // B
    // C
    // D
    // #distinctUntilChanged
  }
}
