/*
 * Copyright (C) 2013-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.actor.ActorSystem

class ImplicitSenderSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with TestKitBase with ImplicitSender {

  implicit lazy val system: ActorSystem = ActorSystem("PekkoCustomSpec")

  override def afterAll() = system.terminate()

  "An ImplicitSender" should {
    "have testActor as its self" in {
      self should ===(testActor)
    }
  }
}
