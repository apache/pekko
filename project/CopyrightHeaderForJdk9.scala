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

import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerSources
import sbt.{ Compile, Def, Test, _ }

object CopyrightHeaderForJdk9 extends AutoPlugin {

  override lazy val requires = CopyrightHeader && Jdk9
  override lazy val trigger = allRequirements

  private lazy val additionalFiles = Def.setting {
    import Jdk9._
    for {
      dir <- additionalSourceDirectories.value ++ additionalTestSourceDirectories.value
      language <- List("java", "scala")
      file <- (dir ** s"*.$language").get
    } yield file
  }

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    Seq(Compile / headerSources ++= additionalFiles.value,
      Test / headerSources ++= additionalFiles.value)
}
