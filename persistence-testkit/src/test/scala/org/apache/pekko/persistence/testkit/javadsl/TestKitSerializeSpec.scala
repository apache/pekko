/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

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
