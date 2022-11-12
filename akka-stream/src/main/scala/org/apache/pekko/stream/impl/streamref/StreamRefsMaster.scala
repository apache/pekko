/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.streamref

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem, Extension, ExtensionId, ExtensionIdProvider }
import pekko.actor.ClassicActorSystemProvider
import pekko.annotation.InternalApi
import pekko.stream.impl.SeqActorName

/** INTERNAL API */
@InternalApi
private[stream] object StreamRefsMaster extends ExtensionId[StreamRefsMaster] with ExtensionIdProvider {

  override def createExtension(system: ExtendedActorSystem): StreamRefsMaster =
    new StreamRefsMaster

  override def lookup: StreamRefsMaster.type = this

  override def get(system: ActorSystem): StreamRefsMaster = super.get(system)
  override def get(system: ClassicActorSystemProvider): StreamRefsMaster = super.get(system)
}

/** INTERNAL API */
@InternalApi
private[stream] final class StreamRefsMaster extends Extension {

  private[this] val sourceRefStageNames = SeqActorName("SourceRef") // "local target"
  private[this] val sinkRefStageNames = SeqActorName("SinkRef") // "remote sender"

  // TODO introduce a master with which all stages running the streams register themselves?

  def nextSourceRefStageName(): String =
    sourceRefStageNames.next()

  def nextSinkRefStageName(): String =
    sinkRefStageNames.next()

}
