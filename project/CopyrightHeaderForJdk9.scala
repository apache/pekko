/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerSources
import sbt.Keys.sourceDirectory
import sbt.{ Compile, Def, Test, _ }

object CopyrightHeaderForJdk9 extends CopyrightHeader {

  override protected def headerMappingSettings: Seq[Def.Setting[_]] = {
    super.headerMappingSettings
    import Jdk9._
    Seq(
      Compile / headerSources ++=
        (((Compile / sourceDirectory).value / SCALA_SOURCE_DIRECTORY) ** "*.scala").get,
      Test / headerSources ++=
        (((Test / sourceDirectory).value / SCALA_TEST_SOURCE_DIRECTORY) ** "*.scala").get,
      Compile / headerSources ++=
        (((Compile / sourceDirectory).value / JAVA_SOURCE_DIRECTORY) ** "*.java").get,
      Test / headerSources ++=
        (((Test / sourceDirectory).value / JAVA_TEST_SOURCE_DIRECTORY) ** "*.java").get)
  }
}
