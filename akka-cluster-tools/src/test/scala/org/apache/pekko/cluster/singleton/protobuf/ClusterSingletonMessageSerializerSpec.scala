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
import pekko.testkit.AkkaSpec

class ClusterSingletonMessageSerializerSpec extends AkkaSpec {

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
