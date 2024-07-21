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

  @nowarn("msg=deprecated")
  def addAkkaConfig(cfg: Config, akkaVersion: String): Config = {
    import scala.collection.JavaConverters._
    val innerSet = cfg.entrySet().asScala
      .filter(e => e.getKey.startsWith("pekko.") && e.getValue.valueType() != ConfigValueType.OBJECT)
      .map { entry =>
        entry.getKey.replace("pekko", "akka") -> adjustPackageNameIfNecessary(entry.getValue)
      }
    var newConfig = cfg
    innerSet.foreach { case (key, value) =>
      newConfig = newConfig.withValue(key, value)
    }
    newConfig.withValue("akka.version", ConfigValueFactory.fromAnyRef(akkaVersion))
  }

  private def adjustPackageNameIfNecessary(cv: ConfigValue): ConfigValue =
    if (cv.valueType() == ConfigValueType.STRING) {
      val str = cv.unwrapped().toString
      if (str.startsWith("org.apache.pekko")) {
        ConfigValueFactory.fromAnyRef(str.replace("org.apache.pekko", "akka"))
      } else {
        cv
      }
    } else {
      cv
    }

}
