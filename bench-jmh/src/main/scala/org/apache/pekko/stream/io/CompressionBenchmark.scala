/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.io

import java.util.concurrent.TimeUnit

import scala.collection.immutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.Done
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream.SystemMaterializer
import pekko.stream.scaladsl.{ Compression, Flow, Sink, Source }
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

/**
 * Compresses and decompresses 16 MiB of JSON-like text split into `chunkSize` elements.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Array(Mode.AverageTime))
class CompressionBenchmark {

  private val TotalBytes = 16 * 1024 * 1024

  implicit val system: ActorSystem = ActorSystem(
    "CompressionBenchmark",
    ConfigFactory.parseString("""
      pekko.loglevel = "OFF"
      pekko.stdout-loglevel = "OFF"
      """).withFallback(ConfigFactory.load()))

  @Param(Array("gzip", "zstd"))
  var algorithm = ""

  @Param(Array("1024", "65536"))
  var chunkSize = 0

  private var chunks: immutable.Seq[ByteString] = _
  private var compressedChunks: immutable.Seq[ByteString] = _
  private var compressFlow: Flow[ByteString, ByteString, NotUsed] = _
  private var decompressFlow: Flow[ByteString, ByteString, NotUsed] = _

  @Setup
  def setup(): Unit = {
    SystemMaterializer(system).materializer

    val random = new Random(42)
    val builder = ByteString.newBuilder
    var i = 0
    while (builder.length < TotalBytes) {
      builder ++= ByteString(
        s"""{"id":$i,"name":"user-${random.nextInt(10000)}","score":${random.nextDouble()},"tags":["a","b","${random
            .alphanumeric
            .take(8)
            .mkString}"]}
""")
      i += 1
    }
    chunks = builder.result().take(TotalBytes).grouped(chunkSize).toVector

    algorithm match {
      case "gzip" =>
        compressFlow = Compression.gzip
        decompressFlow = Compression.gzipDecompress()
      case "zstd" =>
        compressFlow = Compression.zstd
        decompressFlow = Compression.zstdDecompress()
    }
    compressedChunks = Await.result(Source(chunks).via(compressFlow).runWith(Sink.seq), 1.minute)
    val ratio = TotalBytes.toDouble / compressedChunks.map(_.length).sum
    println(f"$algorithm chunkSize=$chunkSize compression ratio $ratio%.2f")
  }

  @TearDown
  def shutdown(): Unit = Await.result(system.terminate(), 5.seconds)

  @Benchmark
  def compress(): Done =
    Await.result(Source(chunks).via(compressFlow).runWith(Sink.ignore), 1.minute)

  @Benchmark
  def decompress(): Done =
    Await.result(Source(compressedChunks).via(decompressFlow).runWith(Sink.ignore), 1.minute)
}
