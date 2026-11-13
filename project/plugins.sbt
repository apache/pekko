/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.5.0")

addSbtPlugin("com.github.sbt" % "sbt-java-formatter" % "0.14.0")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.19.0")
// TODO [sbt2-migration] sbt-bill-of-materials: awaiting a published sbt 2 artifact (lightbend/sbt-bill-of-materials#12)
// addSbtPlugin("com.lightbend.sbt" % "sbt-bill-of-materials" % "1.1.1")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.2")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.10")
addSbtPlugin("com.github.sbt" % "sbt-osgi" % "0.11.0-RC1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.2.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.1.0")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
addSbtPlugin("com.github.sbt" % "sbt-boilerplate" % "0.8.1")

addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")
// TODO [sbt2-migration] sbt-pull-request-validator: sbt 2 cross-build merged; awaiting a published release (sbt/sbt-pull-request-validator#104)
// addSbtPlugin("com.github.sbt" % "sbt-pull-request-validator" % "2.0.0")
// TODO [sbt2-migration] sbt-reproducible-builds: awaiting sbt 2 support (raboof/sbt-reproducible-builds#344)
// addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.35")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.2.0")
// TODO [sbt2-migration] sbt-pekko-build: awaiting sbt 2 support (pjfanning/sbt-pekko-build#21)
// addSbtPlugin("com.github.pjfanning" % "sbt-pekko-build" % "0.4.7")
addSbtPlugin("com.github.reibitto" % "sbt-welcome" % "0.6.0")
addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.10.0")
// TODO [sbt2-migration] sbt-depend-walker: awaiting sbt 2 support (Roiocam/sbt-depend-walker#1)
// addSbtPlugin("io.github.roiocam" % "sbt-depend-walker" % "0.1.1")
addSbtPlugin("com.github.sbt" % "sbt-sbom" % "0.7.0")

// TODO [sbt2-migration] pekko-sbt-paradox: awaiting sbt 2 support (apache/pekko-sbt-paradox#182)
// addSbtPlugin("org.apache.pekko" % "pekko-sbt-paradox" % "1.0.1")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-theme" % "0.11.1")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.11.1")

// TODO [sbt2-migration] sbt-develocity: awaiting a published sbt 2 artifact
// addSbtPlugin("com.gradle" % "sbt-develocity" % "1.4.5")
// TODO [sbt2-migration] sbt-develocity-common-custom-user-data: awaiting a published sbt 2 artifact
// addSbtPlugin("com.gradle" % "sbt-develocity-common-custom-user-data" % "1.5.0")

// https://eed3si9n.com/reducing-scaladoc-file-size-with-sbt-salad-days/
addSbtPlugin("com.eed3si9n" % "sbt-salad-days" % "0.2.0")
