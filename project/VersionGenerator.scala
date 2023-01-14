/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import sbt._
import sbt.Keys._

/**
 * Generate version.conf and org/apache/pekko/Version.scala files based on the version setting.
 */
object VersionGenerator {

  val settings: Seq[Setting[_]] = inConfig(Compile)(
    Seq(
      resourceGenerators += generateVersion(
        resourceManaged, _ / "version.conf",
        """|pekko.version = "%s"
           |"""),
      sourceGenerators += generateVersion(
        sourceManaged, _ / "org" / "apache" / "pekko" / "Version.scala",
        """|package org.apache.pekko
           |
           |object Version {
           |  val current: String = "%s"
           |}
           |""")))

  def generateVersion(dir: SettingKey[File], locate: File => File, template: String) = Def.task[Seq[File]] {
    val file = locate(dir.value)
    val content = template.stripMargin.format(version.value)
    if (!file.exists || IO.read(file) != content) IO.write(file, content)
    Seq(file)
  }

}
