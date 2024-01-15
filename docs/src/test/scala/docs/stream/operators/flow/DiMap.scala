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

package docs.stream.operators.flow

//#imports
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.scaladsl._
//#imports

object DiMap {
  implicit val system: ActorSystem = null

  def demoDiMap(): Unit = {
    // #dimap
    val source = Source(List("1", "2", "3"))
    val flow: Flow[Int, Int, NotUsed] = Flow[Int].map(_ * 2)
    val newFlow: Flow[String, String, NotUsed] = flow.dimap(Integer.parseInt)(_.toString)
    source.via(newFlow).runForeach(println)
    // expected prints:
    // 2
    // 4
    // 6
    // #dimap
  }
}
