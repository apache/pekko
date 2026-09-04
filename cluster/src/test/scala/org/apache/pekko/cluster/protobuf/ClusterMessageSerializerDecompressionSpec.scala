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

package org.apache.pekko.cluster.protobuf

import java.io.{ ByteArrayOutputStream, NotSerializableException }
import java.util.zip.GZIPOutputStream

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.cluster.GossipEnvelope
import pekko.cluster.protobuf.msg.{ ClusterMessages => cm }
import pekko.protobufv3.internal.ByteString
import pekko.testkit.PekkoSpec

class ClusterMessageSerializerDecompressionSpec
    extends PekkoSpec("""
      pekko.actor.provider = cluster
      pekko.serialization.max-decompressed-size = 4 KiB
    """) {

  private val serializer = new ClusterMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  // 8 MiB of zeros gzips to a few KiB, so this is well inside any frame limit but expands
  // far past the 4 KiB configured above.
  private val bomb: Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val zip = new GZIPOutputStream(bos)
    try zip.write(new Array[Byte](8 * 1024 * 1024))
    finally zip.close()
    bos.toByteArray
  }

  "ClusterMessageSerializer" must {

    "reject a Welcome whose payload expands past the maximum" in {
      intercept[NotSerializableException] {
        serializer.fromBinary(bomb, "W")
      }.getMessage should include("max-decompressed-size")
    }

    "reject a GossipEnvelope whose gossip expands past the maximum" in {
      // GossipEnvelope defers decompression until the gossip is read, so the failure
      // surfaces from `gossip` rather than from fromBinary.
      val envelope = cm.GossipEnvelope
        .newBuilder()
        .setFrom(serializer.uniqueAddressToProto(pekko.cluster.Cluster(system).selfUniqueAddress))
        .setTo(serializer.uniqueAddressToProto(pekko.cluster.Cluster(system).selfUniqueAddress))
        .setSerializedGossip(ByteString.copyFrom(bomb))
        .build()

      val msg = serializer.fromBinary(envelope.toByteArray, "GE").asInstanceOf[GossipEnvelope]
      intercept[NotSerializableException] {
        msg.gossip
      }.getMessage should include("max-decompressed-size")
    }

    "still round trip a Welcome that stays within the maximum" in {
      val welcome = pekko.cluster.InternalClusterAction
        .Welcome(pekko.cluster.Cluster(system).selfUniqueAddress, pekko.cluster.Gossip.empty)
      serializer.fromBinary(serializer.toBinary(welcome), "W") should ===(welcome)
    }

    "bound the compressed size at a small fraction of the decompressed size" in {
      // guards the premise of the test above: the rejected payload really is tiny on the wire
      bomb.length should be < (64 * 1024)
    }
  }
}
