/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.pattern

import scala.concurrent.Future

import org.apache.pekko
import pekko.actor.Status
import pekko.testkit.PekkoSpec
import pekko.testkit.TestProbe

class PipeToSpec extends PekkoSpec {

  import system.dispatcher

  "PipeTo" must {

    "work" in {
      val p = TestProbe()
      Future(42).pipeTo(p.ref)
      p.expectMsg(42)
    }

    "signal failure" in {
      val p = TestProbe()
      Future.failed(new Exception("failed")).pipeTo(p.ref)
      p.expectMsgType[Status.Failure].cause.getMessage should ===("failed")
    }

    "pick up an implicit sender()" in {
      val p = TestProbe()
      implicit val s = testActor
      Future(42).pipeTo(p.ref)
      p.expectMsg(42)
      p.lastSender should ===(s)
    }

    "work in Java form" in {
      val p = TestProbe()
      pipe(Future(42)) to p.ref
      p.expectMsg(42)
    }

    "work in Java form with sender()" in {
      val p = TestProbe()
      pipe(Future(42)).to(p.ref, testActor)
      p.expectMsg(42)
      p.lastSender should ===(testActor)
    }

  }

  "PipeToSelection" must {

    "work" in {
      val p = TestProbe()
      val sel = system.actorSelection(p.ref.path)
      Future(42).pipeToSelection(sel)
      p.expectMsg(42)
    }

    "signal failure" in {
      val p = TestProbe()
      val sel = system.actorSelection(p.ref.path)
      Future.failed(new Exception("failed")).pipeToSelection(sel)
      p.expectMsgType[Status.Failure].cause.getMessage should ===("failed")
    }

    "pick up an implicit sender()" in {
      val p = TestProbe()
      val sel = system.actorSelection(p.ref.path)
      implicit val s = testActor
      Future(42).pipeToSelection(sel)
      p.expectMsg(42)
      p.lastSender should ===(s)
    }

    "work in Java form" in {
      val p = TestProbe()
      val sel = system.actorSelection(p.ref.path)
      pipe(Future(42)) to sel
      p.expectMsg(42)
    }

    "work in Java form with sender()" in {
      val p = TestProbe()
      val sel = system.actorSelection(p.ref.path)
      pipe(Future(42)).to(sel, testActor)
      p.expectMsg(42)
      p.lastSender should ===(testActor)
    }

  }

}
