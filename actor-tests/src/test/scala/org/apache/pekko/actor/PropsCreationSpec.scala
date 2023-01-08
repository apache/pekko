/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import scala.annotation.nowarn

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.unused

object PropsCreationSpec {

  final class A

  final class B

  class OneParamActor(@unused blackhole: A) extends Actor {
    override def receive = Actor.emptyBehavior
  }

  class TwoParamActor(@unused blackhole1: A, @unused blackhole2: B) extends Actor {
    override def receive = Actor.emptyBehavior
  }

  object OneParamActorCreator extends pekko.japi.Creator[Actor] {
    override def create(): Actor = new OneParamActor(null)
  }

}

class PropsCreationSpec extends PekkoSpec("""
  # test is using Java serialization and relies on serialize-creators=on
  pekko.actor.serialize-creators = on
  pekko.actor.no-serialization-verification-needed-class-prefix = []
  pekko.actor.allow-java-serialization = on
  pekko.actor.warn-about-java-serializer-usage = off
  """) {

  import org.apache.pekko.actor.PropsCreationSpec._

  "Props" must {
    "work with creator" in {
      val p = Props(new OneParamActor(null))
      system.actorOf(p)
    }
    "work with classOf" in {
      val p = Props(classOf[OneParamActor], null)
      system.actorOf(p)
    }
    "work with classOf + 1 `null` param" in {
      val p = Props(classOf[OneParamActor], null)
      system.actorOf(p)
    }
    "work with classOf + 2 `null` params" in {
      val p = Props(classOf[TwoParamActor], null, null)
      system.actorOf(p)
    }
  }

  "Props Java API" must {
    "work with create(creator)" in {
      @nowarn
      val p = Props.create(OneParamActorCreator)
      system.actorOf(p)
    }
    "work with create(class, creator)" in {
      val p = Props.create(classOf[Actor], OneParamActorCreator)
      system.actorOf(p)
    }
  }

}
