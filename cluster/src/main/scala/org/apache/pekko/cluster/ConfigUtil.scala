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

import com.typesafe.config.{ Config, ConfigValue, ConfigValueFactory, ConfigValueType }

import scala.annotation.nowarn

private[cluster] object ConfigUtil {

  private val PekkoPrefix = "org.apache.pekko"
  private val AkkaPrefix = "akka"

  @nowarn("msg=deprecated")
  def addAkkaConfig(cfg: Config, akkaVersion: String): Config = {
    import org.apache.pekko.util.ccompat.JavaConverters._
    val innerSet = cfg.entrySet().asScala
      .filter(e => e.getKey.startsWith("pekko.") && e.getValue.valueType() != ConfigValueType.OBJECT)
      .map { entry =>
        entry.getKey.replace("pekko", "akka") -> adjustPackageNameToAkkaIfNecessary(entry.getValue)
      }
    var newConfig = cfg
    innerSet.foreach { case (key, value) =>
      newConfig = newConfig.withValue(key, value)
    }
    newConfig.withValue("akka.version", ConfigValueFactory.fromAnyRef(akkaVersion))
  }

  @nowarn("msg=deprecated")
  def changeAkkaToPekkoConfig(cfg: Config): Config = {
    import org.apache.pekko.util.ccompat.JavaConverters._
    val innerSet = cfg.entrySet().asScala
      .filter(e => e.getKey.startsWith("akka.") && e.getValue.valueType() != ConfigValueType.OBJECT)
      .map { entry =>
        entry.getKey.replace("akka", "pekko") -> adjustPackageNameToPekkoIfNecessary(entry.getValue)
      }
    var newConfig = cfg
    innerSet.foreach { case (key, value) =>
      newConfig = newConfig.withValue(key, value)
    }
    newConfig
  }

  def supportsAkkaConfig(cfg: Config): Boolean = {
    cfg
      .getStringList("pekko.remote.accept-protocol-names")
      .contains("akka")
  }

  private def adjustPackageNameToAkkaIfNecessary(cv: ConfigValue): ConfigValue = {
    if (cv.valueType() == ConfigValueType.STRING) {
      val str = cv.unwrapped().toString
      if (str.startsWith(PekkoPrefix)) {
        ConfigValueFactory.fromAnyRef(str.replace(PekkoPrefix, AkkaPrefix))
      } else {
        cv
      }
    } else {
      cv
    }
  }

  private def adjustPackageNameToPekkoIfNecessary(cv: ConfigValue): ConfigValue = {
    if (cv.valueType() == ConfigValueType.STRING) {
      val str = cv.unwrapped().toString
      if (str.startsWith(AkkaPrefix)) {
        ConfigValueFactory.fromAnyRef(str.replace(AkkaPrefix, PekkoPrefix))
      } else {
        cv
      }
    } else {
      cv
    }
  }

}
