/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.cookbook

import scala.concurrent.Await
import scala.concurrent.duration._

class RecipeDigest extends RecipeSpec {

  "Recipe for calculating digest" must {

    "work" in {

      // #calculating-digest
      import java.security.MessageDigest

      import org.apache.pekko
      import pekko.NotUsed
      import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
      import pekko.stream.scaladsl.{ Sink, Source }
      import pekko.util.ByteString

      import pekko.stream.stage._

      val data: Source[ByteString, NotUsed] = Source.single(ByteString("abc"))

      class DigestCalculator(algorithm: String) extends GraphStage[FlowShape[ByteString, ByteString]] {
        val in = Inlet[ByteString]("DigestCalculator.in")
        val out = Outlet[ByteString]("DigestCalculator.out")
        override val shape = FlowShape(in, out)

        override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
          private val digest = MessageDigest.getInstance(algorithm)

          setHandler(out,
            new OutHandler {
              override def onPull(): Unit = pull(in)
            })

          setHandler(in,
            new InHandler {
              override def onPush(): Unit = {
                val chunk = grab(in)
                digest.update(chunk.toArray)
                pull(in)
              }

              override def onUpstreamFinish(): Unit = {
                emit(out, ByteString(digest.digest()))
                completeStage()
              }
            })
        }
      }

      val digest: Source[ByteString, NotUsed] = data.via(new DigestCalculator("SHA-256"))
      // #calculating-digest

      Await.result(digest.runWith(Sink.head), 3.seconds) should be(
        ByteString(0xBA, 0x78, 0x16, 0xBF, 0x8F, 0x01, 0xCF, 0xEA, 0x41, 0x41, 0x40, 0xDE, 0x5D, 0xAE, 0x22, 0x23, 0xB0,
          0x03, 0x61, 0xA3, 0x96, 0x17, 0x7A, 0x9C, 0xB4, 0x10, 0xFF, 0x61, 0xF2, 0x00, 0x15, 0xAD))
    }
  }
}
