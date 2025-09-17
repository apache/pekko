/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

import sbt.Keys._
import sbt._

import com.lightbend.paradox.sbt.ParadoxPlugin
import com.lightbend.paradox.sbt.ParadoxPlugin.autoImport._

object ParadoxBrowse extends AutoPlugin {

  object autoImport {
    lazy val paradoxBrowse = taskKey[Unit]("Open the docs in the default browser")
  }
  import autoImport._

  override lazy val trigger = allRequirements
  override lazy val requires = ParadoxPlugin

  override lazy val projectSettings = Seq(paradoxBrowse := {
    import java.awt.Desktop
    val rootDocFile = (Compile / paradox).value / "index.html"
    val log = streams.value.log
    if (Desktop.isDesktopSupported) Desktop.getDesktop.open(rootDocFile)
    else log.info(s"Couldn't open default browser, but docs are at $rootDocFile")
  })
}
