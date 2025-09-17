/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.concurrent.Future

import org.apache.pekko
import pekko.Done
import pekko.stream.Attributes.Attribute
import pekko.stream._
import pekko.stream.scaladsl.AttributesSpec.AttributesSink
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.Utils._

class LazySinkSpec extends StreamSpec("""
    pekko.stream.materializer.initial-input-buffer-size = 1
    pekko.stream.materializer.max-input-buffer-size = 1
  """) {

  import system.dispatcher
  val ex = TE("")
  case class MyAttribute() extends Attribute
  val myAttributes = Attributes(MyAttribute())

  "A LazySink" must {
    "provide attributes to inner sink" in {
      val attributes = Source
        .single(Done)
        .toMat(Sink.lazyFutureSink(() => Future(Sink.fromGraph(new AttributesSink()))))(Keep.right)
        .addAttributes(myAttributes)
        .run()

      val attribute = attributes.futureValue.get[MyAttribute]
      attribute should contain(MyAttribute())
    }
  }

}
