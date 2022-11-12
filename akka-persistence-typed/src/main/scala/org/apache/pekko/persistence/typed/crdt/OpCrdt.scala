/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.crdt

import org.apache.pekko.annotation.DoNotInherit

@DoNotInherit
trait OpCrdt[Operation] { self =>
  type T <: OpCrdt[Operation] { type T = self.T }

  def applyOperation(op: Operation): T
}
