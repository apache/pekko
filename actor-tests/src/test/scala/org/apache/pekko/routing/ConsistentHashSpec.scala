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

package org.apache.pekko.routing

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConsistentHashSpec extends AnyWordSpec with Matchers {

  // virtual nodes of these two nodes hash to the same ring positions with a factor of 10
  private val nodeA = "node-4230"
  private val nodeB = "node-14323"
  private val nodeC = "node-1"
  private val factor = 10
  private val keys = (0 until 10000).map(i => s"key-$i")

  private def routing(ch: ConsistentHash[String]): Map[String, String] =
    keys.iterator.map(key => key -> ch.nodeFor(key)).toMap

  "ConsistentHash" must {

    "route keys independently of the order the nodes were given" in {
      routing(ConsistentHash(List(nodeA, nodeB, nodeC), factor)) should
      ===(routing(ConsistentHash(List(nodeB, nodeA, nodeC), factor)))
      routing(ConsistentHash(List(nodeA, nodeB, nodeC), factor)) should
      ===(routing(ConsistentHash(List(nodeC, nodeB, nodeA), factor)))
    }

    "route keys independently of the order the nodes were added" in {
      val expected = routing(ConsistentHash(List(nodeA, nodeB, nodeC), factor))
      routing(ConsistentHash(List(nodeC), factor) :+ nodeA :+ nodeB) should ===(expected)
      routing(ConsistentHash(List(nodeC), factor) :+ nodeB :+ nodeA) should ===(expected)
    }

    "restore the colliding virtual nodes of the remaining node when a node is removed" in {
      routing(ConsistentHash(List(nodeA, nodeB, nodeC), factor) :- nodeA) should
      ===(routing(ConsistentHash(List(nodeB, nodeC), factor)))
      routing(ConsistentHash(List(nodeB, nodeA, nodeC), factor) :- nodeA) should
      ===(routing(ConsistentHash(List(nodeB, nodeC), factor)))
      routing(ConsistentHash(List(nodeA, nodeB, nodeC), factor) :- nodeB) should
      ===(routing(ConsistentHash(List(nodeA, nodeC), factor)))
      routing(ConsistentHash(List(nodeB, nodeA, nodeC), factor) :- nodeB) should
      ===(routing(ConsistentHash(List(nodeA, nodeC), factor)))
    }

    "not remove virtual nodes of other nodes when removing a node that is not in the ring" in {
      routing(ConsistentHash(List(nodeB, nodeC), factor) :- nodeA) should
      ===(routing(ConsistentHash(List(nodeB, nodeC), factor)))
    }

    "build the same ring with apply as by adding the nodes one by one" in {
      val many = (0 until 200).map(n => s"node-host-$n") ++ List(nodeA, nodeB, nodeC, nodeA)
      val incremental = many.foldLeft(ConsistentHash(Nil: Seq[String], factor))(_ :+ _)
      routing(ConsistentHash(many, factor)) should ===(routing(incremental))
      routing(ConsistentHash(many.reverse, factor)) should ===(routing(incremental))
    }

    "be empty after all nodes are removed" in {
      (ConsistentHash(List(nodeA, nodeB), factor) :- nodeA :- nodeB).isEmpty should ===(true)
    }

    "not change the ring when a node is added again" in {
      val ch = ConsistentHash(List(nodeA, nodeB, nodeC), factor)
      routing(ch :+ nodeA) should ===(routing(ch))
      routing(ch :+ nodeB) should ===(routing(ch))
    }
  }
}
