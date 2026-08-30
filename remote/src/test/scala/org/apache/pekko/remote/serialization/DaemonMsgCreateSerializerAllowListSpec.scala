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

package org.apache.pekko.remote.serialization

import java.nio.charset.StandardCharsets.UTF_8
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.Deploy
import pekko.actor.Props
import pekko.remote.DaemonMsgCreate
import pekko.remote.NotAllowedClassRemoteDeploymentAttemptException
import pekko.serialization.SerializationExtension
import pekko.serialization.SerializerWithStringManifest
import pekko.testkit.PekkoSpec

object DaemonMsgCreateSerializerAllowListSpec {

  trait EmptyActor extends Actor {
    def receive = Actor.emptyBehavior
  }

  class AllowedActor(@nowarn("msg=never used") arg: MarkerArg) extends EmptyActor
  class NotAllowedActor(@nowarn("msg=never used") arg: MarkerArg) extends EmptyActor
  class SupervisorActor extends EmptyActor

  final case class MarkerArg(value: String)

  /** Counts how often a constructor argument is actually deserialized. */
  val deserializeCount = new AtomicInteger(0)

  class MarkerArgSerializer extends SerializerWithStringManifest {
    override def identifier: Int = 987654
    override def manifest(o: AnyRef): String = "M"
    override def toBinary(o: AnyRef): Array[Byte] = o.asInstanceOf[MarkerArg].value.getBytes(UTF_8)
    override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = {
      deserializeCount.incrementAndGet()
      MarkerArg(new String(bytes, UTF_8))
    }
  }
}

class DaemonMsgCreateSerializerAllowListSpec
    extends PekkoSpec(s"""
    pekko.remote.deployment {
      enable-allow-list = on
      allowed-actor-classes = [
        "org.apache.pekko.remote.serialization.DaemonMsgCreateSerializerAllowListSpec.AllowedActor"
      ]
    }
    pekko.actor {
      serializers.marker-arg = "${classOf[DaemonMsgCreateSerializerAllowListSpec.MarkerArgSerializer].getName}"
      serialization-bindings {
        "org.apache.pekko.remote.serialization.DaemonMsgCreateSerializerAllowListSpec$$MarkerArg" = marker-arg
      }
    }
  """) {

  import DaemonMsgCreateSerializerAllowListSpec._

  private val ser = SerializationExtension(system)
  private val supervisor = system.actorOf(Props[SupervisorActor](), "supervisor")

  private def daemonMsgCreate(actorClass: Class[?]): DaemonMsgCreate =
    DaemonMsgCreate(
      props = Props(actorClass, MarkerArg("payload")),
      deploy = Deploy(),
      path = "foo",
      supervisor = supervisor)

  "DaemonMsgCreateSerializer with the remote deployment allow list enabled" must {

    "deserialize a DaemonMsgCreate for an allow-listed class" in {
      val bytes = ser.serialize(daemonMsgCreate(classOf[AllowedActor])).get
      val got = ser.deserialize(bytes, classOf[DaemonMsgCreate]).get.asInstanceOf[DaemonMsgCreate]
      got.props.clazz should ===(classOf[AllowedActor])
      got.props.args should ===(Seq(MarkerArg("payload")))
    }

    "reject a DaemonMsgCreate for a class that is not allow-listed" in {
      val bytes = ser.serialize(daemonMsgCreate(classOf[NotAllowedActor])).get
      val ex = intercept[NotAllowedClassRemoteDeploymentAttemptException] {
        ser.deserialize(bytes, classOf[DaemonMsgCreate]).get
      }
      ex.getMessage should include("NotAllowedActor")
    }

    "not deserialize the constructor arguments of a rejected class" in {
      val bytes = ser.serialize(daemonMsgCreate(classOf[NotAllowedActor])).get
      deserializeCount.set(0)
      intercept[NotAllowedClassRemoteDeploymentAttemptException] {
        ser.deserialize(bytes, classOf[DaemonMsgCreate]).get
      }
      withClue("peer-supplied constructor arguments must not be deserialized for a rejected class") {
        deserializeCount.get should ===(0)
      }
    }
  }
}

class DaemonMsgCreateSerializerAllowListDisabledSpec extends PekkoSpec(s"""
    pekko.actor {
      serializers.marker-arg = "${classOf[DaemonMsgCreateSerializerAllowListSpec.MarkerArgSerializer].getName}"
      serialization-bindings {
        "org.apache.pekko.remote.serialization.DaemonMsgCreateSerializerAllowListSpec$$MarkerArg" = marker-arg
      }
    }
  """) {

  import DaemonMsgCreateSerializerAllowListSpec._

  private val ser = SerializationExtension(system)
  private val supervisor = system.actorOf(Props[SupervisorActor](), "supervisor")

  "DaemonMsgCreateSerializer with the allow list disabled (the default)" must {

    "deserialize any actor class, as before" in {
      val msg = DaemonMsgCreate(
        props = Props(classOf[NotAllowedActor], MarkerArg("payload")),
        deploy = Deploy(),
        path = "foo",
        supervisor = supervisor)
      val got = ser.deserialize(ser.serialize(msg).get, classOf[DaemonMsgCreate]).get.asInstanceOf[DaemonMsgCreate]
      got.props.clazz should ===(classOf[NotAllowedActor])
      got.props.args should ===(Seq(MarkerArg("payload")))
    }
  }
}
