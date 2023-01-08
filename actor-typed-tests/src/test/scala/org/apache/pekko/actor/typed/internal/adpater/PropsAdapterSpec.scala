/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal.adpater

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko
import pekko.actor
import pekko.actor.typed.Props
import pekko.actor.typed.internal.adapter.PropsAdapter
import pekko.actor.typed.scaladsl.Behaviors

class PropsAdapterSpec extends AnyWordSpec with Matchers {

  "PropsAdapter" should {
    "default to org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox" in {
      val props: Props = Props.empty
      val pa: actor.Props = PropsAdapter(() => Behaviors.empty, props, rethrowTypedFailure = false)
      pa.mailbox shouldEqual "pekko.actor.typed.default-mailbox"
    }
  }
}
