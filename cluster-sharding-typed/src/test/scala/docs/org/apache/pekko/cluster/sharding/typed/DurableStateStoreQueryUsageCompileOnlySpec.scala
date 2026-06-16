/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.cluster.sharding.typed

import scala.annotation.nowarn
import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.persistence.query.{ DeletedDurableState, Offset }
import pekko.stream.scaladsl.Source

@nowarn
object DurableStateStoreQueryUsageCompileOnlySpec {
  def getQuery[Record](system: ActorSystem, pluginId: String, offset: Offset) = {
    // #get-durable-state-store-query-example
    import org.apache.pekko
    import pekko.persistence.state.DurableStateStoreRegistry
    import pekko.persistence.query.scaladsl.DurableStateStoreQuery
    import pekko.persistence.query.DurableStateChange
    import pekko.persistence.query.UpdatedDurableState

    val durableStateStoreQuery =
      DurableStateStoreRegistry(system).durableStateStoreFor[DurableStateStoreQuery[Record]](pluginId)
    val source: Source[DurableStateChange[Record], NotUsed] = durableStateStoreQuery.changes("tag", offset)
    source.map {
      case UpdatedDurableState(persistenceId, revision, value, offset, timestamp) => Some(value)
      case _: DeletedDurableState[_]                                              => None
    }
    // #get-durable-state-store-query-example
  }
}
