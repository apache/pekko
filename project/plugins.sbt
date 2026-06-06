/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.3.1")

addSbtPlugin("com.github.sbt" % "sbt-java-formatter" % "0.12.0")
addSbtPlugin("com.github.sbt.junit" % "sbt-jupiter-interface" % "0.19.0")
// TODO [sbt2-migration] sbt-bill-of-materials: no sbt 2 support, no upstream activity
// addSbtPlugin("com.lightbend.sbt" % "sbt-bill-of-materials" % "1.0.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")
addSbtPlugin("com.github.sbt" % "sbt-osgi" % "0.11.0-RC1")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.5")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.1")
// TODO [sbt2-migration] sbt-api-mappings: no sbt 2 support, stale upstream
// addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")
addSbtPlugin("com.github.sbt" % "sbt-boilerplate" % "0.8.0")

addSbtPlugin("com.github.sbt" % "sbt-header" % "5.11.0")
// TODO [sbt2-migration] sbt-pull-request-validator: PR open https://github.com/sbt/sbt-pull-request-validator/pull/104
// addSbtPlugin("com.github.sbt" % "sbt-pull-request-validator" % "2.0.0")
// TODO [sbt2-migration] sbt-reproducible-builds: blocked upstream on sbt-gpg
// addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.32")

addSbtPlugin("com.github.sbt" % "sbt-dynver" % "5.1.1")
// TODO [sbt2-migration] sbt-source-dist: needs sbt 2 support https://github.com/pjfanning/sbt-source-dist/issues/46
// addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.1.13")
// TODO [sbt2-migration] sbt-pekko-build: needs sbt 2 migration (Pekko-internal)
// addSbtPlugin("com.github.pjfanning" % "sbt-pekko-build" % "0.4.7")
addSbtPlugin("com.github.reibitto" % "sbt-welcome" % "0.5.0")
addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.9.0")
// TODO [sbt2-migration] sbt-depend-walker: no sbt 2 support
// addSbtPlugin("io.github.roiocam" % "sbt-depend-walker" % "0.1.1")
// TODO [sbt2-migration] sbt-sbom: sbt 2 PR merged but not published https://github.com/sbt/sbt-sbom/issues/224
// addSbtPlugin("com.github.sbt" % "sbt-sbom" % "0.5.0")

// TODO [sbt2-migration] pekko-sbt-paradox: needs sbt 2 migration (Pekko-internal)
// addSbtPlugin("org.apache.pekko" % "pekko-sbt-paradox" % "1.0.1")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox-theme" % "0.11.0-M4")
addSbtPlugin("com.lightbend.paradox" % "sbt-paradox" % "0.11.0-M4")

// TODO [sbt2-migration] sbt-develocity: no sbt 2 support, Gradle-maintained
// addSbtPlugin("com.gradle" % "sbt-develocity" % "1.4.5")
