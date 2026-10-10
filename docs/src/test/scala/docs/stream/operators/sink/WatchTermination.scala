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

package docs.stream.operators.sink

import java.nio.file.Paths

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorSystem
import pekko.stream.IOResult
import pekko.stream.scaladsl.{ FileIO, Keep, Sink, Source }
import pekko.util.ByteString

object WatchTermination {
  implicit val system: ActorSystem = ???

  def watchTerminationExample(): Unit = {
    // #watchTermination
    val fileSink: Sink[ByteString, Future[IOResult]] =
      FileIO.toPath(Paths.get("target/watch-termination.txt"))

    // In addition to the IOResult of the file sink, materialize a Future[Done]
    // that only completes once the file has been fully written and closed.
    val (ioResult, terminated): (Future[IOResult], Future[Done]) =
      Source
        .single(ByteString("Hello, world!"))
        .runWith(fileSink.watchTermination(Keep.both))

    // Once `terminated` completes the sink has stopped, including its postStop
    // cleanup, so the file is guaranteed to be closed at this point.
    // #watchTermination
  }
}
