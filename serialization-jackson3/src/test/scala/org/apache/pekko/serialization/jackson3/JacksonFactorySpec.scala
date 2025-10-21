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

import tools.jackson.core.util.JsonRecyclerPools.BoundedPool

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.testkit.TestKit

import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.ConfigFactory

class JacksonFactorySpec extends TestKit(ActorSystem("JacksonFactorySpec"))
    with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private val defaultConfig = ConfigFactory.defaultReference()
  private val objectMapperFactory = new JacksonObjectMapperFactory

  override def afterAll(): Unit = {
    super.afterAll()
    system.terminate()
  }

  "Jackson Factory config" must {
    "support StreamReadConstraints" in {
      val bindingName = "testJackson"
      val maxNumLen = 987
      val maxStringLen = 1234567
      val maxDocLen = 123456789L
      val maxNestingDepth = 5
      val maxTokenCount = 9876543210L
      val config = ConfigFactory.parseString(
        s"""pekko.serialization.jackson3.read.max-number-length=$maxNumLen
           |pekko.serialization.jackson3.read.max-string-length=$maxStringLen
           |pekko.serialization.jackson3.read.max-document-length=$maxDocLen
           |pekko.serialization.jackson3.read.max-nesting-depth=$maxNestingDepth
           |pekko.serialization.jackson3.read.max-token-count=$maxTokenCount
           |""".stripMargin)
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val factory = JacksonObjectMapperProvider.createJsonFactory(
        bindingName, objectMapperFactory, jacksonConfig, None)
      val streamReadConstraints = factory.streamReadConstraints()
      streamReadConstraints.getMaxNumberLength shouldEqual maxNumLen
      streamReadConstraints.getMaxStringLength shouldEqual maxStringLen
      streamReadConstraints.getMaxDocumentLength shouldEqual maxDocLen
      streamReadConstraints.getMaxNestingDepth shouldEqual maxNestingDepth
      streamReadConstraints.getMaxTokenCount shouldEqual maxTokenCount
    }

    "support StreamWriteConstraints" in {
      val bindingName = "testJackson"
      val maxNestingDepth = 54321
      val config = ConfigFactory.parseString(
        s"pekko.serialization.jackson3.write.max-nesting-depth=$maxNestingDepth")
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val factory = JacksonObjectMapperProvider.createJsonFactory(
        bindingName, objectMapperFactory, jacksonConfig, None)
      val streamWriteConstraints = factory.streamWriteConstraints()
      streamWriteConstraints.getMaxNestingDepth shouldEqual maxNestingDepth
    }

    "support BufferRecycler (default)" in {
      val bindingName = "testJackson"
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, defaultConfig)
      val factory = JacksonObjectMapperProvider.createJsonFactory(
        bindingName, objectMapperFactory, jacksonConfig, None)
      val recyclerPool = factory._getRecyclerPool()
      recyclerPool.getClass.getSimpleName shouldEqual "ThreadLocalPool"
    }

    "support BufferRecycler with config override" in {
      val bindingName = "testJackson"
      val poolInstance = "bounded"
      val boundedPoolSize = 1234
      val config = ConfigFactory.parseString(
        s"""pekko.serialization.jackson3.buffer-recycler.pool-instance=$poolInstance
           |pekko.serialization.jackson3.buffer-recycler.bounded-pool-size=$boundedPoolSize
           |""".stripMargin)
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val factory = JacksonObjectMapperProvider.createJsonFactory(
        bindingName, objectMapperFactory, jacksonConfig, None)
      val recyclerPool = factory._getRecyclerPool()
      recyclerPool.getClass.getSimpleName shouldEqual "BoundedPool"
      recyclerPool.asInstanceOf[BoundedPool].capacity() shouldEqual boundedPoolSize
    }
  }
}
