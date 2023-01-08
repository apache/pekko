/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

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
