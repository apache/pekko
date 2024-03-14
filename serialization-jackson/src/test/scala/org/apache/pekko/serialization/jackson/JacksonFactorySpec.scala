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

package org.apache.pekko.serialization.jackson

import com.fasterxml.jackson.core.util.JsonRecyclerPools.BoundedPool
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }
import pekko.testkit.TestKit

class JacksonFactorySpec extends TestKit(ActorSystem("JacksonFactorySpec"))
    with AnyWordSpecLike with Matchers with BeforeAndAfterAll {

  private val defaultConfig = ConfigFactory.defaultReference()
  private val dynamicAccess = system.asInstanceOf[ExtendedActorSystem].dynamicAccess
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
      val config = ConfigFactory.parseString(
        s"""pekko.serialization.jackson.read.max-number-length=$maxNumLen
             |pekko.serialization.jackson.read.max-string-length=$maxStringLen
             |pekko.serialization.jackson.read.max-document-length=$maxDocLen
             |pekko.serialization.jackson.read.max-nesting-depth=$maxNestingDepth
             |""".stripMargin)
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val mapper = JacksonObjectMapperProvider.createObjectMapper(
        bindingName, None, objectMapperFactory, jacksonConfig, dynamicAccess, None)
      val streamReadConstraints = mapper.getFactory.streamReadConstraints()
      streamReadConstraints.getMaxNumberLength shouldEqual maxNumLen
      streamReadConstraints.getMaxStringLength shouldEqual maxStringLen
      streamReadConstraints.getMaxDocumentLength shouldEqual maxDocLen
      streamReadConstraints.getMaxNestingDepth shouldEqual maxNestingDepth
    }
    "support StreamWriteConstraints" in {
      val bindingName = "testJackson"
      val maxNestingDepth = 54321
      val config = ConfigFactory.parseString(
        s"pekko.serialization.jackson.write.max-nesting-depth=$maxNestingDepth")
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val mapper = JacksonObjectMapperProvider.createObjectMapper(
        bindingName, None, objectMapperFactory, jacksonConfig, dynamicAccess, None)
      val streamWriteConstraints = mapper.getFactory.streamWriteConstraints()
      streamWriteConstraints.getMaxNestingDepth shouldEqual maxNestingDepth
    }
    "support BufferRecycler" in {
      val bindingName = "testJackson"
      val poolInstance = "bounded"
      val boundedPoolSize = 1234
      val config = ConfigFactory.parseString(
        s"""pekko.serialization.jackson.buffer-recycler.pool-instance=$poolInstance
           |pekko.serialization.jackson.buffer-recycler.bounded-pool-size=$boundedPoolSize
           |""".stripMargin)
        .withFallback(defaultConfig)
      val jacksonConfig = JacksonObjectMapperProvider.configForBinding(bindingName, config)
      val mapper = JacksonObjectMapperProvider.createObjectMapper(
        bindingName, None, objectMapperFactory, jacksonConfig, dynamicAccess, None)
      val recyclerPool = mapper.getFactory._getRecyclerPool()
      recyclerPool.getClass.getSimpleName shouldEqual "BoundedPool"
      recyclerPool.asInstanceOf[BoundedPool].capacity() shouldEqual boundedPoolSize
    }
  }
}
