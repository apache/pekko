/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed.internal

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.actor.testkit.typed.scaladsl.LogCapturing
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import pekko.actor.typed.scaladsl.Behaviors
import pekko.actor.typed.scaladsl.adapter._
import pekko.cluster.typed.internal.receptionist.ClusterReceptionist
import pekko.serialization.SerializationExtension

class PekkoClusterTypedSerializerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {

  val ref = spawn(Behaviors.empty[String])
  val classicSystem = system.toClassic
  val serializer = new PekkoClusterTypedSerializer(classicSystem.asInstanceOf[ExtendedActorSystem])

  "PekkoClusterTypedSerializer" must {

    Seq("ReceptionistEntry" -> ClusterReceptionist.Entry(ref, 666L)(System.currentTimeMillis())).foreach {
      case (scenario, item) =>
        s"resolve serializer for $scenario" in {
          val serializer = SerializationExtension(classicSystem)
          serializer.serializerFor(item.getClass).getClass should be(classOf[PekkoClusterTypedSerializer])
        }

        s"serialize and de-serialize $scenario" in {
          verifySerialization(item)
        }
    }
  }

  def verifySerialization(msg: AnyRef): Unit =
    serializer.fromBinary(serializer.toBinary(msg), serializer.manifest(msg)) should be(msg)

}
