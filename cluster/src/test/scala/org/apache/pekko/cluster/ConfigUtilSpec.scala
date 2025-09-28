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

package org.apache.pekko.cluster

import org.apache.pekko
import pekko.testkit.PekkoSpec

import com.typesafe.config.ConfigFactory

class ConfigUtilSpec extends PekkoSpec {

  val pekkoSbrClass = "org.apache.pekko.cluster.sbr.SplitBrainResolverProvider"
  val akkaSbrClass = "akka.cluster.sbr.SplitBrainResolverProvider"

  "ConfigUtil" must {
    "support adaptAkkaToPekkoConfig" in {
      val akkaConfig = ConfigFactory.parseString(s"""
        akka.cluster.downing-provider-class = "$akkaSbrClass"
        akka.cluster.split-brain-resolver.active-strategy = keep-majority
        akka.version = "2.6.21"
        """)
      val pekkoConfig = ConfigUtil.adaptAkkaToPekkoConfig(akkaConfig)
      pekkoConfig.getString("pekko.cluster.downing-provider-class") should ===(pekkoSbrClass)
      pekkoConfig.getString("pekko.cluster.split-brain-resolver.active-strategy") should ===("keep-majority")
      pekkoConfig.getString("pekko.version") should ===("2.6.21")
    }
    "support adaptPekkoToAkkaConfig" in {
      val akkaConfig = ConfigFactory.parseString(s"""
        pekko.cluster.downing-provider-class = "$pekkoSbrClass"
        pekko.cluster.split-brain-resolver.active-strategy = keep-majority
        pekko.version = "1.2.3"
        """)
      val pekkoConfig = ConfigUtil.adaptPekkoToAkkaConfig(akkaConfig)
      pekkoConfig.getString("akka.cluster.downing-provider-class") should ===(akkaSbrClass)
      pekkoConfig.getString("akka.cluster.split-brain-resolver.active-strategy") should ===("keep-majority")
      pekkoConfig.getString("akka.version") should ===("1.2.3")
    }
    "support addAkkaConfig" in {
      val akkaConfig = ConfigFactory.parseString(s"""
        pekko.cluster.downing-provider-class = "$pekkoSbrClass"
        pekko.cluster.split-brain-resolver.active-strategy = keep-majority
        pekko.version = "1.2.3"
        """)
      val pekkoConfig = ConfigUtil.addAkkaConfig(akkaConfig, "2.6.21")
      pekkoConfig.getString("akka.cluster.downing-provider-class") should ===(akkaSbrClass)
      pekkoConfig.getString("akka.cluster.split-brain-resolver.active-strategy") should ===("keep-majority")
      pekkoConfig.getString("akka.version") should ===("2.6.21")
    }
  }
}
