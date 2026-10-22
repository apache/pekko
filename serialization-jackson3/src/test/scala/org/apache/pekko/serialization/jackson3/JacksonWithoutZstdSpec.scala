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

package org.apache.pekko.serialization.jackson3

import java.io.File
import java.net.URLClassLoader

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.github.luben.zstd.Zstd

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import com.typesafe.config.ConfigFactory

/**
 * zstd-jni is an optional dependency, so the Jackson serializers must work without it and fail
 * with a clear error when zstd is configured or a zstd payload is received.
 */
class JacksonWithoutZstdSpec extends AnyWordSpec with Matchers {

  "The Jackson serializer" should {
    "work without zstd-jni on the classpath and report zstd use clearly" in {
      // tests run in a forked JVM, so java.class.path is the test classpath
      val urls = System
        .getProperty("java.class.path")
        .split(File.pathSeparator)
        .filterNot(new File(_).getName.startsWith("zstd-jni"))
        .map(new File(_).toURI.toURL)
      val loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader)
      try {
        an[ClassNotFoundException] should be thrownBy loader.loadClass("com.github.luben.zstd.Zstd")

        val zstdPayload = Zstd.compress("{\"name\":\"Bob\"}".getBytes("UTF-8"))
        val check = loader.loadClass(classOf[JacksonWithoutZstdCheck].getName)
        val thread = Thread.currentThread()
        val contextClassLoader = thread.getContextClassLoader
        thread.setContextClassLoader(loader)
        val result =
          try
            check
              .getMethod("run", classOf[Array[Byte]])
              .invoke(check.getDeclaredConstructor().newInstance(), zstdPayload)
          finally thread.setContextClassLoader(contextClassLoader)
        result should ===(
          "gzip:Bob zstd-algorithm:IllegalArgumentException(zstd-jni) zstd-payload:IllegalArgumentException(zstd-jni)")
      } finally loader.close()
    }
  }
}

/** Loaded and run in a class loader without zstd-jni by [[JacksonWithoutZstdSpec]]. */
class JacksonWithoutZstdCheck {
  def run(zstdPayload: Array[Byte]): String = {
    // use this class loader (without zstd-jni) rather than one found from the context or call stack
    val classLoader = getClass.getClassLoader
    val config = ConfigFactory
      .parseString("""
        pekko.serialization.jackson3.jackson-json.compression.compress-larger-than = 0 KiB
        pekko.serialization.jackson3.allowed-class-prefix = ["org.apache.pekko.serialization.jackson3."]
        pekko.serialization.jackson3.jackson-zstd.compression {
          algorithm = zstd
        }
        """)
      .withFallback(ConfigFactory.load(classLoader))
    val system = ActorSystem("JacksonWithoutZstd", Some(config), Some(classLoader), None)
    try {
      val extendedSystem = system.asInstanceOf[ExtendedActorSystem]
      def describe(e: Throwable): String =
        s"${e.getClass.getSimpleName}(${if (e.getMessage.contains("zstd-jni")) "zstd-jni" else e.getMessage})"

      val json = new JacksonJsonSerializer(extendedSystem, "jackson-json")
      val msg = ScalaTestMessages.SimpleCommand("Bob")
      val manifest = json.manifest(msg)
      val gzip = json.fromBinary(json.toBinary(msg), manifest) match {
        case ScalaTestMessages.SimpleCommand(name) => name
        case other                                 => other.toString
      }

      val zstdAlgorithm =
        try {
          new JacksonJsonSerializer(extendedSystem, "jackson-zstd")
          "created"
        } catch { case NonFatal(e) => describe(e) }

      val zstdPayloadResult =
        try json.fromBinary(zstdPayload, manifest).toString
        catch { case NonFatal(e) => describe(e) }

      s"gzip:$gzip zstd-algorithm:$zstdAlgorithm zstd-payload:$zstdPayloadResult"
    } finally {
      system.terminate()
      Await.ready(system.whenTerminated, 10.seconds)
    }
  }
}
