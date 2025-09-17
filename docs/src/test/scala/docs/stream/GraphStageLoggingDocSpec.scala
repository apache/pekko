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

package docs.stream

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.ExecutionContext

import org.apache.pekko
import pekko.stream._
import pekko.stream.scaladsl._
import pekko.testkit.{ EventFilter, PekkoSpec }

class GraphStageLoggingDocSpec extends PekkoSpec("pekko.loglevel = DEBUG") {

  implicit val ec: ExecutionContext = system.dispatcher

  // #operator-with-logging
  import org.apache.pekko.stream.stage.{ GraphStage, GraphStageLogic, OutHandler, StageLogging }

  final class RandomLettersSource extends GraphStage[SourceShape[String]] {
    val out = Outlet[String]("RandomLettersSource.out")
    override val shape: SourceShape[String] = SourceShape(out)

    override def createLogic(inheritedAttributes: Attributes) =
      new GraphStageLogic(shape) with StageLogging {
        setHandler(out,
          new OutHandler {
            override def onPull(): Unit = {
              val c = nextChar() // ASCII lower case letters

              // `log` is obtained from materializer automatically (via StageLogging)
              log.debug("Randomly generated: [{}]", c)

              push(out, c.toString)
            }
          })
      }

    def nextChar(): Char =
      ThreadLocalRandom.current().nextInt('a', 'z'.toInt + 1).toChar
  }
  // #operator-with-logging

  "demonstrate logging in custom graphstage" in {
    val n = 10
    EventFilter.debug(start = "Randomly generated", occurrences = n).intercept {
      Source.fromGraph(new RandomLettersSource).take(n).runWith(Sink.ignore).futureValue
    }
  }

}
