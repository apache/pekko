/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.io

import java.nio.file.{ Files, Paths }

import org.apache.pekko.stream._
import org.apache.pekko.stream.scaladsl.{ FileIO, Sink, Source }
import org.apache.pekko.stream.testkit.Utils._
import org.apache.pekko.util.ByteString
import org.apache.pekko.testkit.PekkoSpec

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class StreamFileDocSpec extends PekkoSpec(UnboundedMailboxConfig) {

  implicit val ec: ExecutionContext = system.dispatcher

  // silence sysout
  def println(s: String) = ()

  val file = Files.createTempFile(getClass.getName, ".tmp")

  override def afterTermination() = Files.delete(file)

  {
    // #file-source
    import org.apache.pekko.stream.scaladsl._
    // #file-source
    Thread.sleep(0) // needs a statement here for valid syntax and to avoid "unused" warnings
  }

  {
    // #file-source
    val file = Paths.get("example.csv")
    // #file-source
  }

  {
    // #file-sink
    val file = Paths.get("greeting.txt")
    // #file-sink
  }

  "read data from a file" in {
    // #file-source
    def handle(b: ByteString): Unit // #file-source
    = ()

    // #file-source

    val foreach: Future[IOResult] = FileIO.fromPath(file).to(Sink.ignore).run()
    // #file-source
  }

  "configure dispatcher in code" in {
    // #custom-dispatcher-code
    FileIO.fromPath(file).withAttributes(ActorAttributes.dispatcher("custom-blocking-io-dispatcher"))
    // #custom-dispatcher-code
  }

  "write data into a file" in {
    // #file-sink
    val text = Source.single("Hello Pekko Stream!")
    val result: Future[IOResult] = text.map(t => ByteString(t)).runWith(FileIO.toPath(file))
    // #file-sink
  }
}
