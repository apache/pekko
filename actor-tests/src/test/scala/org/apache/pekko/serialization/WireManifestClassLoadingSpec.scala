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

package org.apache.pekko.serialization

import java.io.NotSerializableException
import java.nio.charset.StandardCharsets.UTF_8

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.testkit.PekkoSpec

object WireManifestClassLoadingSpec {

  /** A plain `Serializer` that does not want a type hint. */
  class NoManifestSerializer(@annotation.nowarn("msg=never used") val system: ExtendedActorSystem) extends Serializer {
    override def identifier: Int = 9911
    override def includeManifest: Boolean = false
    override def toBinary(o: AnyRef): Array[Byte] = o.toString.getBytes(UTF_8)
    override def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef = {
      // a serializer declaring includeManifest = false must never be handed a type hint
      if (manifest.isDefined)
        throw new AssertionError(s"unexpected type hint [${manifest.get.getName}]")
      new String(bytes, UTF_8)
    }
  }

  /** A plain `Serializer` that does want a type hint. */
  class WithManifestSerializer(@annotation.nowarn("msg=never used") val system: ExtendedActorSystem)
      extends Serializer {
    override def identifier: Int = 9912
    override def includeManifest: Boolean = true
    override def toBinary(o: AnyRef): Array[Byte] = o.toString.getBytes(UTF_8)
    override def fromBinary(bytes: Array[Byte], manifest: Option[Class[?]]): AnyRef =
      new String(bytes, UTF_8) + ":" + manifest.map(_.getName).getOrElse("none")
  }
}

class WireManifestClassLoadingSpec
    extends PekkoSpec(s"""
    pekko.actor.serializers {
      no-manifest = "${classOf[WireManifestClassLoadingSpec.NoManifestSerializer].getName}"
      with-manifest = "${classOf[WireManifestClassLoadingSpec.WithManifestSerializer].getName}"
    }
  """) {

  private val serialization = SerializationExtension(system)
  private val payload = "hello".getBytes(UTF_8)

  "Deserialization of a wire-supplied manifest" must {

    "not resolve a class for a serializer that declares includeManifest = false" in {
      // A hostile or non-conforming peer can put any string in the manifest field. For a
      // serializer that ignores the hint there is no reason to turn it into a class load.
      val result = serialization.deserialize(payload, 9911, "com.example.NotOnTheClasspath").get
      result should ===("hello")
    }

    "still resolve a class for a serializer that declares includeManifest = true" in {
      val result = serialization.deserialize(payload, 9912, classOf[String].getName).get
      result should ===("hello:java.lang.String")
    }

    "still fail for an unknown manifest class when the serializer wants the hint" in {
      val ex = intercept[NotSerializableException] {
        serialization.deserialize(payload, 9912, "com.example.NotOnTheClasspath").get
      }
      ex.getMessage should include("com.example.NotOnTheClasspath")
    }
  }
}
