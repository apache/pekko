/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.scaladsl

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.testkit._

class TestKitNotSerializeSpec extends CommonTestKitTests {

  override lazy val system = initSystemWithEnabledPlugin("TestKitNotSerializeSpec", false, false)

  import testKit._

  override def specificTests() = "save next nonserializable persisted" in {
    val pid = randomPid()
    val a = system.actorOf(Props(classOf[A], pid, None))
    val c = new C
    a ! c

    expectNextPersisted(pid, c)
  }

}
