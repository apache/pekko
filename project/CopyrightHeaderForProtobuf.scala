/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{ headerMappings, headerSources, HeaderFileType }
import sbt.Keys.sourceDirectory
import sbt.{ inConfig, Compile, Def, Test, _ }

object CopyrightHeaderForProtobuf extends CopyrightHeader {
  override protected def headerMappingSettings: Seq[Def.Setting[_]] = {
    super.headerMappingSettings
    Seq(Compile, Test).flatMap { config =>
      inConfig(config) {
        Seq(
          config / headerSources ++=
            (((config / sourceDirectory).value / "protobuf") ** "*.proto").get,
          headerMappings := headerMappings.value ++ Map(HeaderFileType("proto") -> cStyleComment))
      }
    }
  }
}
