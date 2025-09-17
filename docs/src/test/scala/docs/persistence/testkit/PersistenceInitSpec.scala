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

package docs.persistence.testkit

import java.util.UUID

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.Done
import pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit

//#imports
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._

import org.apache.pekko.persistence.testkit.scaladsl.PersistenceInit
//#imports

class PersistenceInitSpec extends ScalaTestWithActorTestKit(s"""
  pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
  pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
  pekko.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
  """) with AnyWordSpecLike {

  "PersistenceInit" should {
    "initialize plugins" in {
      // #init
      val timeout = 5.seconds
      val done: Future[Done] = PersistenceInit.initializeDefaultPlugins(system, timeout)
      Await.result(done, timeout)
      // #init
    }
  }
}
