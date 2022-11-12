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
