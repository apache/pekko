/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import org.apache.pekko.testkit.PekkoSpec
import scala.annotation.nowarn

@nowarn // for keeping imports
class FlowPrependSpec extends PekkoSpec {

//#prepend

//#prepend

  "An Prepend flow" should {

    "work in entrance example" in {
      // #prepend
      val ladies = Source(List("Emma", "Emily"))
      val gentlemen = Source(List("Liam", "William"))

      gentlemen.prepend(ladies).runWith(Sink.foreach(println))
      // this will print "Emma", "Emily", "Liam", "William"
      // #prepend
    }

    "work in lazy entrance example" in {
      // #prependLazy
      val ladies = Source(List("Emma", "Emily"))
      val gentlemen = Source(List("Liam", "William"))

      gentlemen.prependLazy(ladies).runWith(Sink.foreach(println))
      // this will print "Emma", "Emily", "Liam", "William"
      // #prependLazy
    }
  }
}
