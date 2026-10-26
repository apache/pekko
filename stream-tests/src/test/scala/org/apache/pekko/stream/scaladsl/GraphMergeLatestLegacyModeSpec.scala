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

package org.apache.pekko.stream.scaladsl

import scala.concurrent.duration._

import org.apache.pekko
import pekko.stream._
import pekko.stream.testkit._
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.StreamTestKit._
import pekko.stream.testkit.scaladsl.TestSource

import com.typesafe.config.ConfigFactory

class GraphMergeLatestLegacyModeSpec
    extends StreamSpec(ConfigFactory.parseString("""
      pekko.stream.materializer.merge-latest.mode = "legacy"
    """)) {
  import GraphDSL.Implicits._

  "MergeLatest in legacy mode" must {
    "retain every update while downstream is back-pressured" in assertAllStagesStopped {
      val up1 = TestSource[Int]()
      val up2 = TestSource[Int]()
      val probe = TestSubscriber.manualProbe[List[Int]]()

      val (in1, in2) = RunnableGraph
        .fromGraph(GraphDSL.createGraph(up1, up2)((_, _)) { implicit b => (s1, s2) =>
          val merge = b.add(MergeLatest[Int](2))

          s1         ~> merge
          s2         ~> merge
          merge.out  ~> Sink.fromSubscriber(probe)
          ClosedShape
        })
        .run()

      val subscription = probe.expectSubscription()

      in1.sendNext(1)
      in2.sendNext(2)
      in1.sendNext(3)
      probe.expectNoMessage(10.millis)

      subscription.request(1)
      probe.expectNext(List(1, 2))
      subscription.request(1)
      probe.expectNext(List(3, 2))

      in1.sendComplete()
      in2.sendComplete()
      probe.expectComplete()
    }
  }
}
