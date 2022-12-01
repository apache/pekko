/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence.testkit

import java.util.UUID

import org.apache.pekko.Done
import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

//#imports
import org.apache.pekko.persistence.testkit.scaladsl.PersistenceInit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

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
