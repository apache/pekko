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

package org.apache.pekko.stream.io.compression

import java.io.File
import java.net.URLClassLoader
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.javadsl
import pekko.stream.scaladsl.{ Compression, Sink, Source }
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * zstd-jni is an optional dependency of pekko-stream, so the other compression operators must
 * work without it on the classpath.
 */
class CompressionWithoutZstdSpec extends AnyWordSpec with Matchers {

  "Compression" should {
    "support gzip and deflate without zstd-jni on the classpath" in {
      // stream-tests run in a forked JVM, so java.class.path is the test classpath
      val urls = System
        .getProperty("java.class.path")
        .split(File.pathSeparator)
        .filterNot(new File(_).getName.startsWith("zstd-jni"))
        .map(new File(_).toURI.toURL)
      // not closed: dispatcher shutdown tasks of the terminated ActorSystem may still load classes from it
      val loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader)
      an[ClassNotFoundException] should be thrownBy loader.loadClass("com.github.luben.zstd.Zstd")

      val roundTrip = loader.loadClass(classOf[CompressionWithoutZstdRoundTrip].getName)
      val thread = Thread.currentThread()
      val contextClassLoader = thread.getContextClassLoader
      thread.setContextClassLoader(loader)
      val result =
        try roundTrip.getMethod("run").invoke(roundTrip.getDeclaredConstructor().newInstance())
        finally thread.setContextClassLoader(contextClassLoader)
      result should ===("gzip:hello deflate:hello javadsl:hello zstd-level:3")
    }
  }
}

/** Loaded and run in a class loader without zstd-jni by [[CompressionWithoutZstdSpec]]. */
class CompressionWithoutZstdRoundTrip {
  def run(): String = {
    // use this class loader (without zstd-jni) rather than one found from the context or call stack
    val classLoader = getClass.getClassLoader
    // a fatal error in this nested ActorSystem must fail the test, not exit the forked test JVM
    val config = ConfigFactory
      .parseString("pekko.jvm-exit-on-fatal-error = off")
      .withFallback(ConfigFactory.load(classLoader))
    val system = ActorSystem("CompressionWithoutZstd", Some(config), Some(classLoader), None)
    try {
      def roundTrip(source: Source[ByteString, Any]): String =
        Await.result(source.runWith(Sink.fold(ByteString.empty)(_ ++ _))(pekko.stream.Materializer(system)), 10.seconds)
          .utf8String
      val data = Source.single(ByteString("hello"))
      val gzip = roundTrip(data.via(Compression.gzip).via(Compression.gzipDecompress()))
      val deflate = roundTrip(data.via(Compression.deflate).via(Compression.inflate()))
      val javaDeflate = roundTrip(data.via(javadsl.Compression.deflate).via(javadsl.Compression.inflate(1024)))
      s"gzip:$gzip deflate:$deflate javadsl:$javaDeflate zstd-level:${Compression.ZstdDefaultCompressionLevel}"
    } finally {
      system.terminate()
      Await.ready(system.whenTerminated, FiniteDuration(10, TimeUnit.SECONDS))
    }
  }
}
