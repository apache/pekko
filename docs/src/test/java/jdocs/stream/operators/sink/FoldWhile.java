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

import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;

public class FoldWhile {
  private ActorSystem system = null;

  public void foldWhileUsage() throws Exception {
    // #foldWhile
    final int result =
        Source.range(1, 10)
            .runWith(Sink.foldWhile(0, acc -> acc < 10, Integer::sum), system)
            .toCompletableFuture()
            .get();
    System.out.println(result);
    // Expect prints:
    // 10
    // #foldWhile
  }
}
