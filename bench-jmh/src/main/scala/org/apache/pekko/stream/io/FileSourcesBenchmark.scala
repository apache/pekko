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

package org.apache.pekko.stream.io

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.Attributes
import pekko.stream.IOResult
import pekko.stream.scaladsl._
import pekko.util.ByteString

/**
 * Benchmark                         (bufSize)  Mode  Cnt    Score    Error  Units
 * FileSourcesBenchmark.fileChannel       2048  avgt  100  1140.192 ± 55.184  ms/op
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class FileSourcesBenchmark {

  implicit val system: ActorSystem = ActorSystem("file-sources-benchmark")

  val file: Path = {
    val line = ByteString("x" * 2048 + "\n")

    val f = Files.createTempFile(getClass.getName, ".bench.tmp")

    val ft = Source
      .fromIterator(() => Iterator.continually(line))
      .take(10 * 39062) // adjust as needed
      .runWith(FileIO.toPath(f))
    Await.result(ft, 30.seconds)

    f
  }

  @Param(Array("2048"))
  var bufSize = 0

  var fileChannelSource: Source[ByteString, Future[IOResult]] = _
  var fileInputStreamSource: Source[ByteString, Future[IOResult]] = _
  var ioSourceLinesIterator: Source[ByteString, NotUsed] = _

  @Setup
  def setup(): Unit = {
    fileChannelSource = FileIO.fromPath(file, bufSize)
    fileInputStreamSource = StreamConverters.fromInputStream(() => Files.newInputStream(file), bufSize)
    ioSourceLinesIterator =
      Source.fromIterator(() => scala.io.Source.fromFile(file.toFile).getLines()).map(ByteString(_))
  }

  @TearDown
  def teardown(): Unit = {
    Files.delete(file)
  }

  @TearDown
  def shutdown(): Unit = {
    system.terminateAndAwait(Duration.Inf)
  }

  @Benchmark
  def fileChannel(): Unit = {
    val h = fileChannelSource.to(Sink.ignore).run()

    Await.result(h, 30.seconds)
  }

  @Benchmark
  def fileChannel_noReadAhead(): Unit = {
    val h = fileChannelSource.withAttributes(Attributes.inputBuffer(1, 1)).to(Sink.ignore).run()

    Await.result(h, 30.seconds)
  }

  @Benchmark
  def inputStream(): Unit = {
    val h = fileInputStreamSource.to(Sink.ignore).run()

    Await.result(h, 30.seconds)
  }

  /**
   * The previous status quo was very slow:
   * Benchmark                                         Mode  Cnt     Score      Error  Units
   * FileSourcesBenchmark.naive_ioSourceLinesIterator  avgt   20  7067.944 ± 1341.847  ms/op
   */
  @Benchmark
  def naive_ioSourceLinesIterator(): Unit = {
    val p = Promise[Done]()
    ioSourceLinesIterator.to(Sink.onComplete(p.complete(_))).run()

    Await.result(p.future, 30.seconds)
  }

}
