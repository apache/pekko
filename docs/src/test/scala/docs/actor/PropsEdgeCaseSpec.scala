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

package docs.actor

import org.apache.pekko.actor.{ Actor, Props }
import docs.CompileOnlySpec
import org.scalatest.wordspec.AnyWordSpec

//#props-edge-cases-value-class
case class MyValueClass(v: Int) extends AnyVal

//#props-edge-cases-value-class

class PropsEdgeCaseSpec extends AnyWordSpec with CompileOnlySpec {
  "value-class-edge-case-example" in compileOnlySpec {
    // #props-edge-cases-value-class-example
    class ValueActor(value: MyValueClass) extends Actor {
      def receive = {
        case multiplier: Long => sender() ! (value.v * multiplier)
      }
    }
    val valueClassProp = Props(classOf[ValueActor], MyValueClass(5)) // Unsupported
    // #props-edge-cases-value-class-example

    // #props-edge-cases-default-values
    class DefaultValueActor(a: Int, b: Int = 5) extends Actor {
      def receive = {
        case x: Int => sender() ! ((a + x) * b)
      }
    }

    val defaultValueProp1 = Props(classOf[DefaultValueActor], 2.0) // Unsupported

    class DefaultValueActor2(b: Int = 5) extends Actor {
      def receive = {
        case x: Int => sender() ! (x * b)
      }
    }
    val defaultValueProp2 = Props[DefaultValueActor2]() // Unsupported
    val defaultValueProp3 = Props(classOf[DefaultValueActor2]) // Unsupported
    // #props-edge-cases-default-values
  }
}
