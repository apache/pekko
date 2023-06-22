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

package org.apache.pekko.cluster.ddata

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Optimization for add/remove followed by merge and merge should just fast forward to
 * the new instance.
 *
 * It's like a cache between calls of the same thread, you can think of it as a thread local.
 * The Replicator actor invokes the user's modify function, which returns a new ReplicatedData instance,
 * with the ancestor field set (see for example the add method in ORSet). Then (in same thread) the
 * Replication calls merge, which makes use of the ancestor field to perform quick merge
 * (see for example merge method in ORSet).
 *
 * It's not thread safe if the modifying function and merge are called from different threads,
 * i.e. if used outside the Replicator infrastructure, but the worst thing that can happen is that
 * a full merge is performed instead of the fast forward merge.
 */
@InternalApi private[pekko] trait FastMerge { self: ReplicatedData =>

  private var ancestor: FastMerge = null

  /** INTERNAL API: should be called from "updating" methods, and `resetDelta` */
  private[pekko] def assignAncestor(newData: T with FastMerge): T = {
    newData.ancestor = if (this.ancestor eq null) this else this.ancestor
    this.ancestor = null // only one level, for GC
    newData
  }

  /** INTERNAL API: should be used from merge */
  private[pekko] def isAncestorOf(that: T with FastMerge): Boolean =
    that.ancestor eq this

  /** INTERNAL API: should be called from merge */
  private[pekko] def clearAncestor(): self.type = {
    ancestor = null
    this
  }

}
