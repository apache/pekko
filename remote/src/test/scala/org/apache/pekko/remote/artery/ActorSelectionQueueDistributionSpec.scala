/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.remote.artery

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ActorSelectionQueueDistributionSpec extends AnyWordSpec with Matchers {

  val OrdinaryQueueIndex = 2

  def computeQueueIndex(elements: Seq[String], outboundLanes: Int): Int = {
    if (outboundLanes == 1) OrdinaryQueueIndex
    else OrdinaryQueueIndex + ((elements.hashCode() & Int.MaxValue) % outboundLanes)
  }

  "ActorSelection queue distribution" must {

    "distribute different target paths across lanes" in {
      val outboundLanes = 3
      val paths = (1 to 100).map(i => Seq("user", s"echo$i"))
      val queueIndices = paths.map(p => computeQueueIndex(p, outboundLanes))
      val distinctLanes = queueIndices.distinct

      distinctLanes.size should be > 1
      distinctLanes.foreach { idx =>
        idx should be >= OrdinaryQueueIndex
        idx should be < (OrdinaryQueueIndex + outboundLanes)
      }
    }

    "produce non-negative queue indices even for negative hash codes" in {
      val outboundLanes = 3
      val paths = (1 to 1000).map(i => Seq("user", s"actor$i"))
      val queueIndices = paths.map(p => computeQueueIndex(p, outboundLanes))

      queueIndices.foreach { idx =>
        idx should be >= OrdinaryQueueIndex
      }
    }

    "preserve ordering for same target path" in {
      val outboundLanes = 3
      val path = Seq("user", "echoA")
      val indices = (1 to 10).map(_ => computeQueueIndex(path, outboundLanes))

      indices.distinct.size should be(1)
    }

    "use single lane when outboundLanes is 1" in {
      val paths = (1 to 10).map(i => Seq("user", s"echo$i"))
      val queueIndices = paths.map(p => computeQueueIndex(p, 1))

      queueIndices.distinct should be(Seq(OrdinaryQueueIndex))
    }

    "not concentrate all paths on lane 0 (original bug)" in {
      val outboundLanes = 3
      val paths = Seq(
        Seq("user", "echoA2"),
        Seq("user", "echoB2"),
        Seq("user", "echoC2"))

      val queueIndices = paths.map(p => computeQueueIndex(p, outboundLanes))
      val laneOffsets = queueIndices.map(_ - OrdinaryQueueIndex)

      laneOffsets should not be Seq(0, 0, 0)
    }
  }
}
