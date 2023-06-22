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
import java.util.ArrayList
import java.util.Collections
import org.apache.pekko
import pekko.util.ccompat.JavaConverters._
import pekko.actor.ExtendedActorSystem
import pekko.cluster.ddata.GSet
import pekko.cluster.ddata.protobuf.SerializationSupport
import pekko.serialization.Serializer
import docs.ddata.TwoPhaseSet
import docs.ddata.protobuf.msg.TwoPhaseSetMessages

class TwoPhaseSetSerializer(val system: ExtendedActorSystem) extends Serializer with SerializationSupport {

  override def includeManifest: Boolean = false

  override def identifier = 99999

  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: TwoPhaseSet => twoPhaseSetToProto(m).toByteArray
    case _              => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass}")
  }

  override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    twoPhaseSetFromBinary(bytes)
  }

  def twoPhaseSetToProto(twoPhaseSet: TwoPhaseSet): TwoPhaseSetMessages.TwoPhaseSet = {
    val b = TwoPhaseSetMessages.TwoPhaseSet.newBuilder()
    // using java collections and sorting for performance (avoid conversions)
    val adds = new ArrayList[String]
    twoPhaseSet.adds.elements.foreach(adds.add)
    if (!adds.isEmpty) {
      Collections.sort(adds)
      b.addAllAdds(adds)
    }
    val removals = new ArrayList[String]
    twoPhaseSet.removals.elements.foreach(removals.add)
    if (!removals.isEmpty) {
      Collections.sort(removals)
      b.addAllRemovals(removals)
    }
    b.build()
  }

  def twoPhaseSetFromBinary(bytes: Array[Byte]): TwoPhaseSet = {
    val msg = TwoPhaseSetMessages.TwoPhaseSet.parseFrom(bytes)
    val addsSet = msg.getAddsList.iterator.asScala.toSet
    val removalsSet = msg.getRemovalsList.iterator.asScala.toSet
    val adds = addsSet.foldLeft(GSet.empty[String])((acc, el) => acc.add(el))
    val removals = removalsSet.foldLeft(GSet.empty[String])((acc, el) => acc.add(el))
    // GSet will accumulate deltas when adding elements,
    // but those are not of interest in the result of the deserialization
    TwoPhaseSet(adds.resetDelta, removals.resetDelta)
  }
}
//#serializer

class TwoPhaseSetSerializerWithCompression(system: ExtendedActorSystem) extends TwoPhaseSetSerializer(system) {
  // #compression
  override def toBinary(obj: AnyRef): Array[Byte] = obj match {
    case m: TwoPhaseSet => compress(twoPhaseSetToProto(m))
    case _              => throw new IllegalArgumentException(s"Can't serialize object of type ${obj.getClass}")
  }

  override def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    twoPhaseSetFromBinary(decompress(bytes))
  }
  // #compression
}
