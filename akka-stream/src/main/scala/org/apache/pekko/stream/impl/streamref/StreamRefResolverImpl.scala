/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.streamref

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.stream.SinkRef
import pekko.stream.SourceRef
import pekko.stream.StreamRefResolver

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final class StreamRefResolverImpl(system: ExtendedActorSystem) extends StreamRefResolver {

  def toSerializationFormat[T](ref: SourceRef[T]): String = ref match {
    case SourceRefImpl(actorRef) =>
      actorRef.path.toSerializationFormatWithAddress(system.provider.getDefaultAddress)
    case other => throw new IllegalArgumentException(s"Unexpected SourceRef impl: ${other.getClass}")
  }

  def toSerializationFormat[T](ref: SinkRef[T]): String = ref match {
    case SinkRefImpl(actorRef) =>
      actorRef.path.toSerializationFormatWithAddress(system.provider.getDefaultAddress)
    case other => throw new IllegalArgumentException(s"Unexpected SinkRef impl: ${other.getClass}")
  }

  def resolveSourceRef[T](serializedSourceRef: String): SourceRef[T] =
    SourceRefImpl(system.provider.resolveActorRef(serializedSourceRef))

  def resolveSinkRef[T](serializedSinkRef: String): SinkRef[T] =
    SinkRefImpl(system.provider.resolveActorRef(serializedSinkRef))
}
