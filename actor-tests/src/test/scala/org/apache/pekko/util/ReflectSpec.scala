/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import scala.annotation.nowarn
import scala.collection.immutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object ReflectSpec {
  final class A
  final class B

  class One(@nowarn("msg=never used") a: A)
  class Two(@nowarn("msg=never used") a: A, @nowarn("msg=never used") b: B)

  class MultipleOne(a: A, b: B) {
    def this(a: A) = this(a, null)
    def this(b: B) = this(null, b)
  }
}

class ReflectSpec extends AnyWordSpec with Matchers {

  import org.apache.pekko.util.ReflectSpec._

  "Reflect#findConstructor" must {

    "deal with simple 1 matching case" in {
      Reflect.findConstructor(classOf[One], immutable.Seq(new A))
    }
    "deal with 2-params 1 matching case" in {
      Reflect.findConstructor(classOf[Two], immutable.Seq(new A, new B))
    }
    "deal with 2-params where one is `null` 1 matching case" in {
      Reflect.findConstructor(classOf[Two], immutable.Seq(new A, null))
      Reflect.findConstructor(classOf[Two], immutable.Seq(null, new B))
    }
    "deal with `null` in 1 matching case" in {
      val constructor = Reflect.findConstructor(classOf[One], immutable.Seq(null))
      constructor.newInstance(null)
    }
    "deal with multiple constructors" in {
      Reflect.findConstructor(classOf[MultipleOne], immutable.Seq(new A))
      Reflect.findConstructor(classOf[MultipleOne], immutable.Seq(new B))
      Reflect.findConstructor(classOf[MultipleOne], immutable.Seq(new A, new B))
    }
    "throw when multiple matching constructors" in {
      intercept[IllegalArgumentException] {
        Reflect.findConstructor(classOf[MultipleOne], immutable.Seq(null))
      }
    }
  }

  "Reflect#getCallerClass" must {

    "be defined on JDK 17+ (StackWalker is always available)" in {
      Reflect.getCallerClass shouldBe defined
    }

    "return a non-null class for a valid stack depth" in {
      val getCaller = Reflect.getCallerClass.get
      // Frame 0 is the lambda inside Reflect$ calling StackWalker.walk
      val frame0 = getCaller(0)
      frame0 should not be null
      frame0.getName should startWith("org.apache.pekko.util.Reflect")
    }

    "return null for index beyond stack depth" in {
      val getCaller = Reflect.getCallerClass.get
      getCaller(Int.MaxValue) shouldBe null
    }
  }

  "Reflect#findClassLoader" must {

    "return a non-null ClassLoader" in {
      val cl = Reflect.findClassLoader()
      cl should not be null
    }
  }

}
