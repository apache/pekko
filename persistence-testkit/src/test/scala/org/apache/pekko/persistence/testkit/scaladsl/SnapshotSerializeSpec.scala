/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.scaladsl

import java.io.NotSerializableException

import org.apache.pekko
import pekko.actor.Props
import pekko.persistence.SaveSnapshotFailure
import pekko.persistence.testkit._

class SnapshotSerializeSpec extends CommonSnapshotTests {

  override lazy val system = initSystemWithEnabledPlugin("SnapshotSerializeSpec", true, true)

  override def specificTests(): Unit =
    "fail if tries to save nonserializable snapshot" in {
      val pid = randomPid()
      val a = system.actorOf(Props(classOf[A], pid, Some(testActor)))
      a ! NewSnapshot(new C)

      expectMsg((List.empty, 0L))
      expectMsgPF() { case SaveSnapshotFailure(_, _: NotSerializableException) => }
    }

}
