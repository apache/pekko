/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.ddata.typed

import org.apache.pekko
import pekko.cluster.{ ddata => dd }

package object scaladsl {

  /**
   * @see [[pekko.cluster.ddata.ReplicatorSettings]].
   */
  type ReplicatorSettings = dd.ReplicatorSettings
}
