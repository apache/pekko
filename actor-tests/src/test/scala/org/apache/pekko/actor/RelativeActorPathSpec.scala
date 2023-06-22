/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import java.net.URLEncoder

import scala.collection.immutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RelativeActorPathSpec extends AnyWordSpec with Matchers {

  def elements(path: String): immutable.Seq[String] = RelativeActorPath.unapply(path).getOrElse(Nil)

  "RelativeActorPath" must {
    "match single name" in {
      elements("foo") should ===(List("foo"))
    }
    "match path separated names" in {
      elements("foo/bar/baz") should ===(List("foo", "bar", "baz"))
    }
    "match url encoded name" in {
      val name = URLEncoder.encode("pekko://ClusterSystem@127.0.0.1:7355", "UTF-8")
      elements(name) should ===(List(name))
    }
    "match path with uid fragment" in {
      elements("foo/bar/baz#1234") should ===(List("foo", "bar", "baz#1234"))
    }
  }
}
