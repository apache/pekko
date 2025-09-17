/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.converters

// #import
import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, InputStream, OutputStream }

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.IOResult
import pekko.stream.scaladsl.{ Flow, Keep, Sink, Source, StreamConverters }
import pekko.util.ByteString

import scala.util.Random
// #import
import scala.concurrent.Future

import org.scalatest.concurrent.Futures

import org.apache.pekko.testkit.PekkoSpec

class ToFromJavaIOStreams extends PekkoSpec with Futures {

  "demonstrate conversion from java.io.streams" in {

    // #tofromJavaIOStream
    val bytes = "Some random input".getBytes
    val inputStream = new ByteArrayInputStream(bytes)
    val outputStream = new ByteArrayOutputStream()

    val source: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => inputStream)

    val toUpperCase: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString].map(_.map(_.toChar.toUpper.toByte))

    val sink: Sink[ByteString, Future[IOResult]] = StreamConverters.fromOutputStream(() => outputStream)

    val eventualResult = source.via(toUpperCase).runWith(sink)

    // #tofromJavaIOStream
    whenReady(eventualResult) { _ =>
      outputStream.toByteArray.map(_.toChar).mkString should be("SOME RANDOM INPUT")
    }

  }

  "demonstrate usage as java.io.InputStream" in {
    // #asJavaInputStream
    val toUpperCase: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString].map(_.map(_.toChar.toUpper.toByte))
    val source: Source[ByteString, NotUsed] = Source.single(ByteString("some random input"))
    val sink: Sink[ByteString, InputStream] = StreamConverters.asInputStream()

    val inputStream: InputStream = source.via(toUpperCase).runWith(sink)
    // #asJavaInputStream
    inputStream.read() should be('S')
    inputStream.close()
  }

  "demonstrate usage as java.io.OutputStream" in {
    // #asJavaOutputStream
    val source: Source[ByteString, OutputStream] = StreamConverters.asOutputStream()
    val sink: Sink[ByteString, Future[ByteString]] = Sink.fold[ByteString, ByteString](ByteString.empty)(_ ++ _)

    val (outputStream, result): (OutputStream, Future[ByteString]) =
      source.toMat(sink)(Keep.both).run()

    // #asJavaOutputStream
    val bytesArray = Array.fill[Byte](3)(Random.nextInt(1024).asInstanceOf[Byte])
    outputStream.write(bytesArray)
    outputStream.close()
    result.futureValue should be(ByteString(bytesArray))
  }

}
