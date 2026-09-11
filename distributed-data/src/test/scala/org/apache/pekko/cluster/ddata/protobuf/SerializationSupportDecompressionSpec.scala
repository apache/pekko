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

package org.apache.pekko.cluster.ddata.protobuf

import java.io.{ ByteArrayOutputStream, NotSerializableException }
import java.util.zip.GZIPOutputStream

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.cluster.ddata.ORSet
import pekko.testkit.PekkoSpec

class SerializationSupportDecompressionSpec
    extends PekkoSpec("""
      pekko.actor.provider = cluster
      pekko.remote.artery.canonical.port = 0
      pekko.serialization.max-decompressed-size = 4 KiB
    """) {

  // `SerializationSupport` is a public trait mixed into serializers whose `system` is a
  // constructor parameter, so the maximum is read per call rather than held in a field.
  private val serializer = new ReplicatedDataSerializer(system.asInstanceOf[ExtendedActorSystem])

  private def gzip(bytes: Array[Byte]): Array[Byte] = {
    val bos = new ByteArrayOutputStream()
    val zip = new GZIPOutputStream(bos)
    try zip.write(bytes)
    finally zip.close()
    bos.toByteArray
  }

  "SerializationSupport" must {

    "reject a payload that expands past the maximum" in {
      intercept[NotSerializableException] {
        serializer.decompress(gzip(new Array[Byte](8 * 1024 * 1024)))
      }.getMessage should include("max-decompressed-size")
    }

    "still round trip a payload within the maximum" in {
      val payload = Array.tabulate[Byte](1024)(i => (i % 251).toByte)
      serializer.decompress(gzip(payload)) should ===(payload)
    }

    "still round trip a compressed ORSet" in {
      val orset = ORSet().add(pekko.cluster.Cluster(system).selfUniqueAddress, "a")
      val manifest = serializer.manifest(orset)
      serializer.fromBinary(serializer.toBinary(orset), manifest) should ===(orset)
    }
  }
}
