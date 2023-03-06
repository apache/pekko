/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.client.protobuf

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.cluster.client.ClusterReceptionist.Internal._
import pekko.testkit.PekkoSpec

@nowarn("msg=deprecated")
class ClusterClientMessageSerializerSpec extends PekkoSpec {

  val serializer = new ClusterClientMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    val blob = serializer.toBinary(obj)
    val ref = serializer.fromBinary(blob, serializer.manifest(obj))
    ref should ===(obj)
  }

  "ClusterClientMessages" must {

    "be serializable" in {
      val contactPoints = Vector(
        "pekko://system@node-1:7355/system/receptionist",
        "pekko://system@node-2:7355/system/receptionist",
        "pekko://system@node-3:7355/system/receptionist")
      checkSerialization(Contacts(contactPoints))
      checkSerialization(GetContacts)
      checkSerialization(Heartbeat)
      checkSerialization(HeartbeatRsp)
      checkSerialization(ReceptionistShutdown)
    }
  }
}
