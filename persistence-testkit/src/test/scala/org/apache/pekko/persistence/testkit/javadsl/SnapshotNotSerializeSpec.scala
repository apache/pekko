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

package org.apache.pekko.persistence.testkit.javadsl

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.testkit._

class SnapshotNotSerializeSpec extends CommonSnapshotTests {

  override lazy val system = initSystemWithEnabledPlugin("SnapshotNotSerializeSpec", false, false)

  import testKit._

  override def specificTests(): Unit =
    "succeed if trying to save nonserializable snapshot" in {
      val pid = randomPid()
      val a = system.actorOf(Props(classOf[A], pid, Some(testActor)))
      val c = new C
      a ! NewSnapshot(c)

      expectNextPersisted(pid, c)
    }

}
