/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")

addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-bill-of-materials" % "1.0.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.33")
addSbtPlugin("com.github.sbt" % "sbt-osgi" % "0.9.4-INVALID-CEN-JAR-PATCH")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "1.1.3")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
addSbtPlugin("com.github.sbt" % "sbt-boilerplate" % "0.7.0")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.10.0")
addSbtPlugin("com.github.sbt" % "sbt-pull-request-validator" % "2.0.0")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.31")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.1.10")
addSbtPlugin("com.github.pjfanning" % "sbt-pekko-build" % "0.3.2")
addSbtPlugin("com.github.reibitto" % "sbt-welcome" % "0.2.2")
addSbtPlugin("com.github.sbt" % "sbt-license-report" % "1.5.0")

resolvers += Resolver.ApacheMavenSnapshotsRepo

addSbtPlugin("org.apache.pekko" % "pekko-sbt-paradox" % "1.0.1-RC1+3-2b1f8708-SNAPSHOT")
