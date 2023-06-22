/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed

import com.typesafe.config.ConfigFactory

import org.apache.pekko.actor.typed.scaladsl.DispatcherSelectorSpec

class ClusterDispatcherSelectorSpec
    extends DispatcherSelectorSpec(ConfigFactory.parseString("""
    pekko.actor.provider = cluster
    """).withFallback(DispatcherSelectorSpec.config)) {

  // same tests as in DispatcherSelectorSpec

}
