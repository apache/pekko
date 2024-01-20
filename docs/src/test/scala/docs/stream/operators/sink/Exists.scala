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

//#imports
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl._

import scala.concurrent.ExecutionContext
//#imports

object Exists {

  implicit val system: ActorSystem = null
  implicit val ec: ExecutionContext = system.dispatcher

  def detectAnomaly(): Unit = {
    // #exists
    val source = Source(Seq("Sun is shining", "Unidentified Object", "River is flowing"))

    val anomalies = Seq("Unidentified Object")
    def isAnomaly(phenomenon: String): Boolean = anomalies.contains(phenomenon)

    val result = source.runWith(Sink.exists(isAnomaly))
    result.map(println)
    // expected print:
    // true
    // #exists
  }

}
