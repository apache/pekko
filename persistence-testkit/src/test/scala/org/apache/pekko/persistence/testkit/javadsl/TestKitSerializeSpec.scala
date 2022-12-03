/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.javadsl

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.testkit._

class TestKitSerializeSpec extends CommonTestKitTests {
  override lazy val system = initSystemWithEnabledPlugin("TestKitSerializeSpec", true, true)

  override def specificTests(): Unit = "fail next nonserializable persisted" in {
    val pid = randomPid()
    val a = system.actorOf(Props(classOf[A], pid, None))
    a ! new C

    watch(a)
    expectTerminated(a)
  }
}
