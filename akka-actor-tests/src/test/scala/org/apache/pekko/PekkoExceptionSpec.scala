/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import org.apache.pekko.actor._

/**
 * A spec that verified that the PekkoException has at least a single argument constructor of type String.
 *
 * This is required to make Pekko Exceptions be friends with serialization/deserialization.
 */
class PekkoExceptionSpec extends AnyWordSpec with Matchers {

  "PekkoException" must {
    "have a PekkoException(String msg) constructor to be serialization friendly" in {
      // if the call to this method completes, we know what there is at least a single constructor which has
      // the expected argument type.
      verify(classOf[PekkoException])

      // lets also try it for the exception that triggered this bug to be discovered.
      verify(classOf[ActorKilledException])
    }
  }

  def verify(clazz: java.lang.Class[_]): Unit = {
    clazz.getConstructor(Array(classOf[String]): _*)
  }
}
