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

package docs.ddata.protobuf

//#serializer
import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.cluster.ddata.GSet
import pekko.cluster.ddata.protobuf.SerializationSupport
import pekko.serialization.Serializer
import docs.ddata.TwoPhaseSet
import docs.ddata.protobuf.msg.TwoPhaseSetMessages

class TwoPhaseSetSerializer2(val system: ExtendedActorSystem) extends Serializer with SerializationSupport {

  override def includeManifest: Boolean = false

  override def identifier = 99999

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: TwoPhaseSet => twoPhaseSetToProto(m).toByteArray
    case _              => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass}")
  }

  override def fromBinary(bytes: Array[Byte], clazz: Option[Class[?]]): AnyRef = {
    twoPhaseSetFromBinary(bytes)
  }

  def twoPhaseSetToProto(twoPhaseSet: TwoPhaseSet): TwoPhaseSetMessages.TwoPhaseSet2 = {
    val b = TwoPhaseSetMessages.TwoPhaseSet2.newBuilder()
    if (!twoPhaseSet.adds.isEmpty)
      b.setAdds(otherMessageToProto(twoPhaseSet.adds).toByteString())
    if (!twoPhaseSet.removals.isEmpty)
      b.setRemovals(otherMessageToProto(twoPhaseSet.removals).toByteString())
    b.build()
  }

  def twoPhaseSetFromBinary(bytes: Array[Byte]): TwoPhaseSet = {
    val msg = TwoPhaseSetMessages.TwoPhaseSet2.parseFrom(bytes)
    val adds =
      if (msg.hasAdds)
        otherMessageFromBinary(msg.getAdds.toByteArray).asInstanceOf[GSet[String]]
      else
        GSet.empty[String]
    val removals =
      if (msg.hasRemovals)
        otherMessageFromBinary(msg.getRemovals.toByteArray).asInstanceOf[GSet[String]]
      else
        GSet.empty[String]
    TwoPhaseSet(adds, removals)
  }
}
//#serializer
