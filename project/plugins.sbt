addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "2.1.1")

addSbtPlugin("com.lightbend.sbt" % "sbt-java-formatter" % "0.7.0")
addSbtPlugin("com.lightbend.sbt" % "sbt-bill-of-materials" % "1.0.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.4.6")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.33")
// sbt-osgi 0.9.5 is available but breaks including jdk9-only classes
// sbt-osgi 0.9.6 is available but breaks populating pekko-protobuf-v3
addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.9.4")
addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.8.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.6.1")

addSbtPlugin("de.heikoseeberger" % "sbt-header" % "5.7.0")
addSbtPlugin("com.hpe.sbt" % "sbt-pull-request-validator" % "1.0.0")
addSbtPlugin("net.bzzt" % "sbt-reproducible-builds" % "0.30")

addSbtPlugin("com.dwijnand" % "sbt-dynver" % "4.1.1")
addSbtPlugin("com.lightbend.sbt" % "sbt-publish-rsync" % "0.2")
addSbtPlugin("com.github.pjfanning" % "sbt-source-dist" % "0.1.5")
addSbtPlugin("org.mdedetrich" % "sbt-apache-sonatype" % "0.1.6")
addSbtPlugin("com.github.reibitto" % "sbt-welcome" % "0.2.2")

// allow access to snapshots for pekko-sbt-paradox
resolvers += "Apache Nexus Snapshots".at("https://repository.apache.org/content/repositories/snapshots/")

// We have to deliberately use older versions of sbt-paradox because current Pekko sbt build
// only loads on JDK 1.8 so we need to bring in older versions of parboiled which support JDK 1.8
addSbtPlugin(("org.apache.pekko" % "pekko-sbt-paradox" % "0.0.0+38-68da3106-SNAPSHOT").excludeAll(
  "com.lightbend.paradox", "sbt-paradox",
  "com.lightbend.paradox" % "sbt-paradox-apidoc",
  "com.lightbend.paradox" % "sbt-paradox-project-info"))
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox" % "0.9.2").force())
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox-apidoc" % "0.10.1").force())
addSbtPlugin(("com.lightbend.paradox" % "sbt-paradox-project-info" % "2.0.0").force())
