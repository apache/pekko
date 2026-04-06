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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ GatherCollector, Gatherer, Source }

object Gather {

  implicit val actorSystem: ActorSystem = ???

  def zipWithIndex(): Unit = {
    // #zipWithIndex
    Source(List("A", "B", "C", "D"))
      .gather(() =>
        new Gatherer[String, (String, Long)] {
          private var index = 0L

          override def apply(elem: String, collector: GatherCollector[(String, Long)]): Unit = {
            collector.push((elem, index))
            index += 1
          }
        })
      .runForeach(println)
    // prints
    // (A,0)
    // (B,1)
    // (C,2)
    // (D,3)
    // #zipWithIndex
  }

  def bufferUntilChanged(): Unit = {
    // #bufferUntilChanged
    Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
      .gather(() =>
        new Gatherer[String, List[String]] {
          private var buffer = List.empty[String]

          override def apply(elem: String, collector: GatherCollector[List[String]]): Unit =
            buffer match {
              case head :: _ if head != elem =>
                collector.push(buffer.reverse)
                buffer = elem :: Nil
              case _ =>
                buffer = elem :: buffer
            }

          override def onComplete(collector: GatherCollector[List[String]]): Unit =
            if (buffer.nonEmpty)
              collector.push(buffer.reverse)
        })
      .runForeach(println)
    // prints
    // List(A)
    // List(B, B)
    // List(C, C, C)
    // List(D)
    // #bufferUntilChanged
  }

  def distinctUntilChanged(): Unit = {
    // #distinctUntilChanged
    Source("A" :: "B" :: "B" :: "C" :: "C" :: "C" :: "D" :: Nil)
      .gather(() =>
        new Gatherer[String, String] {
          private var lastElement: Option[String] = None

          override def apply(elem: String, collector: GatherCollector[String]): Unit =
            lastElement match {
              case Some(last) if last == elem =>
              case _ =>
                lastElement = Some(elem)
                collector.push(elem)
            }
        })
      .runForeach(println)
    // prints
    // A
    // B
    // C
    // D
    // #distinctUntilChanged
  }
}
