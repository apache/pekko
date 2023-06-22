/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.singleton.protobuf

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverDone
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverInProgress
import pekko.cluster.singleton.ClusterSingletonManager.Internal.HandOverToMe
import pekko.cluster.singleton.ClusterSingletonManager.Internal.TakeOverFromMe
import pekko.testkit.PekkoSpec

class ClusterSingletonMessageSerializerSpec extends PekkoSpec {

  val serializer = new ClusterSingletonMessageSerializer(system.asInstanceOf[ExtendedActorSystem])

  def checkSerialization(obj: AnyRef): Unit = {
    val blob = serializer.toBinary(obj)
    val ref = serializer.fromBinary(blob, serializer.manifest(obj))
    ref should ===(obj)
  }

  "ClusterSingletonMessages" must {

    "be serializable" in {
      checkSerialization(HandOverDone)
      checkSerialization(HandOverInProgress)
      checkSerialization(HandOverToMe)
      checkSerialization(TakeOverFromMe)
    }
  }
}
