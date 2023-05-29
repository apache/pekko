/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ headerMappings, headerSources, HeaderFileType }
import sbt.Keys.baseDirectory
import sbt.{ inConfig, Compile, Def, PluginTrigger, Test, _ }

object CopyrightHeaderForBuild extends CopyrightHeader {
  override def trigger: PluginTrigger = noTrigger

  override def projectSettings: Seq[Def.Setting[_]] = {
    Seq(Compile, Test).flatMap { config =>
      inConfig(config) {
        Seq(
          config / headerSources ++= (((config / baseDirectory).value / "project") ** ("*.scala" || "*.sbt")).get,
          config / headerSources ++= ((config / baseDirectory).value ** "*.sbt").get,
          headerMappings := headerMappings.value ++ Map(HeaderFileType("sbt") -> cStyleComment),
          headerMappings := headerMappings.value ++ Map(HeaderFileType.scala -> cStyleComment))
      }
    }
  }
}
