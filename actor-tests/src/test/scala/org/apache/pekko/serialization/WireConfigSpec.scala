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

package org.apache.pekko.serialization

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }

import scala.jdk.CollectionConverters._

import org.apache.pekko.testkit.PekkoSpec

import com.typesafe.config.{ ConfigFactory, ConfigRenderOptions }

class WireConfigSpec extends PekkoSpec {

  // a file the parser must not read when a message asks it to
  private val secretFile: Path = {
    val f = Files.createTempFile("wire-config-spec", ".conf")
    Files.write(f, "secret = leaked".getBytes(StandardCharsets.UTF_8))
    f
  }
  private val filePath = secretFile.toAbsolutePath.toString
  private val fileUrl = secretFile.toUri.toString

  override def afterTermination(): Unit = Files.deleteIfExists(secretFile)

  "WireConfig" must {

    "parse ordinary HOCON" in {
      val config = WireConfig.parseString("a = 1\nb { c = two }")
      config.getInt("a") should ===(1)
      config.getString("b.c") should ===("two")
    }

    "parse what a serializer writes" in {
      // every serializer renders config with ConfigRenderOptions.concise
      val rendered = ConfigFactory.parseString("pekko.cluster.roles = [a, b]").root.render(ConfigRenderOptions.concise())
      WireConfig.parseString(rendered).getStringList("pekko.cluster.roles").asScala.toList should ===(List("a", "b"))
    }

    "not read a file named by an include" in {
      val config = WireConfig.parseString(s"include file(\"$filePath\")\na = 1")
      config.hasPath("secret") should ===(false)
      config.getInt("a") should ===(1)
    }

    "not read a file named by a required include" in {
      WireConfig.parseString(s"include required(file(\"$filePath\"))\na = 1").hasPath("secret") should ===(false)
    }

    "not fetch a URL named by an include" in {
      // a file: URL stands in for an outbound request, so the test needs no network
      val config = WireConfig.parseString(s"include url(\"$fileUrl\")\na = 1")
      config.hasPath("secret") should ===(false)
      config.getInt("a") should ===(1)
    }

    "not read a resource named by a classpath include" in {
      // reference.conf is on the test classpath, so the default includer would pull it in
      WireConfig.parseString("include classpath(\"reference.conf\")\na = 1").hasPath("pekko.version") should ===(false)
    }

    "differ from the default parser, which does resolve all three" in {
      // guards the premise of the tests above: these directives really do resolve without the
      // includer, so those tests are checking the change rather than an inert directive
      ConfigFactory.parseString(s"include file(\"$filePath\")").hasPath("secret") should ===(true)
      ConfigFactory.parseString(s"include url(\"$fileUrl\")").hasPath("secret") should ===(true)
      ConfigFactory.parseString("include classpath(\"reference.conf\")").hasPath("pekko.version") should ===(true)
    }
  }
}
