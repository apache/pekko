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

import java.nio.file.Paths;
import java.util.concurrent.CompletionStage;
import org.apache.pekko.Done;
import org.apache.pekko.actor.ActorSystem;
import org.apache.pekko.japi.Pair;
import org.apache.pekko.stream.IOResult;
import org.apache.pekko.stream.javadsl.FileIO;
import org.apache.pekko.stream.javadsl.Keep;
import org.apache.pekko.stream.javadsl.Sink;
import org.apache.pekko.stream.javadsl.Source;
import org.apache.pekko.util.ByteString;

public class WatchTermination {

  private ActorSystem system = null;

  void example() {
    // #watchTermination
    final Sink<ByteString, CompletionStage<IOResult>> fileSink =
        FileIO.toPath(Paths.get("target/watch-termination.txt"));

    // In addition to the IOResult of the file sink, materialize a CompletionStage<Done>
    // that only completes once the file has been fully written and closed.
    final Pair<CompletionStage<IOResult>, CompletionStage<Done>> result =
        Source.single(ByteString.fromString("Hello, world!"))
            .runWith(fileSink.watchTermination(Keep.both()), system);

    final CompletionStage<IOResult> ioResult = result.first();
    final CompletionStage<Done> terminated = result.second();

    // Once `terminated` completes the sink has stopped, including its postStop
    // cleanup, so the file is guaranteed to be closed at this point.
    // #watchTermination
  }
}
