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

package org.apache.pekko.persistence.serialization

import java.nio.ByteOrder
import java.util.Base64

import annotation.nowarn

import org.apache.pekko
import pekko.persistence.fsm.PersistentFSM.PersistentFSMSnapshot
import pekko.serialization.{ SerializationExtension, Serializers }
import pekko.testkit.PekkoSpec
import pekko.util.SWARUtil

@nowarn("msg=deprecated")
private[serialization] object SnapshotSerializerTestData {
  val fsmSnapshot = PersistentFSMSnapshot[String]("test-identifier", "test-data", None)
  // https://github.com/apache/pekko/pull/837#issuecomment-1847320309
  val akkaSnapshotData =
    "PAAAAAcAAABha2thLnBlcnNpc3RlbmNlLmZzbS5QZXJzaXN0ZW50RlNNJFBlcnNpc3RlbnRGU01TbmFwc2hvdAoPdGVzdC1pZGVudGlmaWVyEg0IFBIJdGVzdC1kYXRh"
}

class SnapshotSerializerSpec extends PekkoSpec {

  import SnapshotSerializerTestData._

  "Snapshot serializer" should {
    "deserialize akka snapshots" in {
      val serialization = SerializationExtension(system)
      val bytes = Base64.getDecoder.decode(akkaSnapshotData)
      val result = serialization.deserialize(bytes, classOf[Snapshot]).get
      val deserialized = result.data
      deserialized shouldBe a[PersistentFSMSnapshot[_]]
      val persistentFSMSnapshot = deserialized.asInstanceOf[PersistentFSMSnapshot[_]]
      persistentFSMSnapshot shouldEqual fsmSnapshot
    }
    "deserialize pekko snapshots" in {
      val serialization = SerializationExtension(system)
      val bytes = serialization.serialize(Snapshot(fsmSnapshot)).get
      val result = serialization.deserialize(bytes, classOf[Snapshot]).get
      val deserialized = result.data
      deserialized shouldBe a[PersistentFSMSnapshot[_]]
      val persistentFSMSnapshot = deserialized.asInstanceOf[PersistentFSMSnapshot[_]]
      persistentFSMSnapshot shouldEqual fsmSnapshot
    }
    "deserialize pre-saved pekko snapshots" in {
      val serialization = SerializationExtension(system)
      // this is Pekko encoded snapshot based on https://github.com/apache/pekko/pull/837#issuecomment-1847320309
      val pekkoSnapshotData =
        "SAAAAAcAAABvcmcuYXBhY2hlLnBla2tvLnBlcnNpc3RlbmNlLmZzbS5QZXJzaXN0ZW50RlNNJFBlcnNpc3RlbnRGU01TbmFwc2hvdAoPdGVzdC1pZGVudGlmaWVyEg0IFBIJdGVzdC1kYXRh"
      val bytes = Base64.getDecoder.decode(pekkoSnapshotData)
      val result = serialization.deserialize(bytes, classOf[Snapshot]).get
      val deserialized = result.data
      deserialized shouldBe a[PersistentFSMSnapshot[_]]
      val persistentFSMSnapshot = deserialized.asInstanceOf[PersistentFSMSnapshot[_]]
      persistentFSMSnapshot shouldEqual fsmSnapshot
    }
    "produce binary format with header length in first 4 bytes (little-endian)" in {
      val serialization = SerializationExtension(system)
      val bytes = serialization.serialize(Snapshot(fsmSnapshot)).get
      // bytes[0..3] = header length as little-endian int32
      val headerLength = SWARUtil.getInt(bytes, 0, ByteOrder.LITTLE_ENDIAN)
      headerLength should be > 0
      headerLength should be < bytes.length
      // header occupies bytes[4 .. 4+headerLength)
      // remaining bytes are the snapshot payload
      val payloadLength = bytes.length - 4 - headerLength
      payloadLength should be > 0
    }
    "produce binary format with serializer ID in header (little-endian)" in {
      val serialization = SerializationExtension(system)
      val bytes = serialization.serialize(Snapshot(fsmSnapshot)).get
      val headerLength = SWARUtil.getInt(bytes, 0, ByteOrder.LITTLE_ENDIAN)
      // header bytes[4..7] = serializer ID as little-endian int32
      val serializerId = SWARUtil.getInt(bytes, 4, ByteOrder.LITTLE_ENDIAN)
      val snapshotSerializer = serialization.findSerializerFor(fsmSnapshot)
      serializerId shouldEqual snapshotSerializer.identifier
      // header bytes[8..4+headerLength) = manifest as UTF-8 string
      val manifestBytes = bytes.slice(8, 4 + headerLength)
      val manifest = new String(manifestBytes, "UTF-8")
      manifest shouldEqual Serializers.manifestFor(snapshotSerializer, fsmSnapshot)
    }
    "serialize and deserialize a simple string snapshot" in {
      val serialization = SerializationExtension(system)
      val snapshot = Snapshot("hello pekko")
      val bytes = serialization.serialize(snapshot).get
      val result = serialization.deserialize(bytes, classOf[Snapshot]).get
      result.data shouldEqual "hello pekko"
    }
    "serialized bytes start with header length followed by header" in {
      val serialization = SerializationExtension(system)
      val snapshot = Snapshot("test")
      val bytes = serialization.serialize(snapshot).get
      // verify overall structure: total = 4 (headerLenField) + headerLen + payloadLen
      val headerLength = SWARUtil.getInt(bytes, 0, ByteOrder.LITTLE_ENDIAN)
      bytes.length shouldEqual (4 + headerLength + serialization.findSerializerFor("test").toBinary("test").length)
    }
  }
}
