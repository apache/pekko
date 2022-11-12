/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import com.typesafe.config.ConfigFactory

import org.apache.pekko.actor.typed.scaladsl.DispatcherSelectorSpec

class ClusterDispatcherSelectorSpec
    extends DispatcherSelectorSpec(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    """).withFallback(DispatcherSelectorSpec.config)) {

  // same tests as in DispatcherSelectorSpec

}
