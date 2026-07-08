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

import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.TimeoutException

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

  final class StringOnly(val value: String)
  final class PrivateConstructor private (val value: String)
  object PrivateConstructor {
    def direct(value: String): PrivateConstructor = new PrivateConstructor(value)
  }
  final class ThrowingConstructor(@nowarn("msg=never used") value: String) {
    throw new IllegalArgumentException("user-bug")
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
      constructor.invoke(null.asInstanceOf[AnyRef])
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
    "use public lookup for public JDK constructors in non-open modules" in {
      val constructor = Reflect.findConstructor(classOf[TimeoutException], immutable.Seq("err"))
      val instance = Reflect.instantiate[TimeoutException](constructor, immutable.Seq("err"))
      instance.getMessage should ===("err")
    }
    "not confuse null with an Object argument across lookups" in {
      Reflect.instantiate(classOf[StringOnly], immutable.Seq(null)).value shouldBe null
      intercept[IllegalArgumentException] {
        Reflect.findConstructor(classOf[StringOnly], immutable.Seq(new Object))
      }.getMessage should include("no matching constructor")
    }
    "access private constructors when the package is open" in {
      Reflect.instantiate(classOf[PrivateConstructor], immutable.Seq("private")).value should ===("private")
    }
    "preserve InvocationTargetException for constructor failures" in {
      val exception = intercept[InvocationTargetException] {
        Reflect.instantiate(classOf[ThrowingConstructor], immutable.Seq("ignored"))
      }
      exception.getCause shouldBe a[IllegalArgumentException]
      exception.getCause.getMessage should ===("user-bug")
    }
    "not wrap constructor class-initialization failures" in {
      val exception = intercept[ExceptionInInitializerError] {
        Reflect.instantiate(classOf[FailingInitializerConstructor])
      }
      exception.getCause.getMessage should ===("constructor-initializer-bug")
    }
    "invoke varargs constructors as fixed arity" in {
      val processBuilder =
        Reflect.instantiate(classOf[ProcessBuilder], immutable.Seq(Array("echo", "fixed-arity")))
      processBuilder.command().toArray should ===(Array[AnyRef]("echo", "fixed-arity"))
    }
  }

  "Reflect#invokeStaticNoArg" must {
    "preserve InvocationTargetException for target failures" in {
      val clazz = classOf[StaticMethodExceptionFixture]
      val callerLookup = MethodHandles.lookup()
      val method = Reflect.findStaticNoArgMethod(clazz, "failInBody", callerLookup)
      val exception = intercept[InvocationTargetException] {
        Reflect.invokeStaticNoArg[AnyRef](clazz, method, callerLookup)
      }
      exception.getCause.getMessage should ===("static-method-user-bug")
    }

    "not wrap static method class-initialization failures" in {
      val clazz = classOf[FailingInitializerStaticMethod]
      val callerLookup = MethodHandles.lookup()
      val method = Reflect.findStaticNoArgMethod(clazz, "getInstance", callerLookup)
      val exception = intercept[ExceptionInInitializerError] {
        Reflect.invokeStaticNoArg[AnyRef](clazz, method, callerLookup)
      }
      exception.getCause.getMessage should ===("static-method-initializer-bug")
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
