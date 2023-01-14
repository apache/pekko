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

package docs.stream.operators.sink

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Keep
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

object Lazy {

  implicit val system: ActorSystem = ???

  def example(): Unit = {
    // #simple-example
    val matVal =
      Source
        .maybe[String]
        .map { element =>
          println(s"mapped $element")
          element
        }
        .toMat(Sink.lazySink { () =>
          println("Sink created")
          Sink.foreach(elem => println(s"foreach $elem"))
        })(Keep.left)
        .run()

    // some time passes
    // nothing has been printed
    matVal.success(Some("one"))
    // now prints:
    // mapped one
    // Sink created
    // foreach one

    // #simple-example
  }
}
