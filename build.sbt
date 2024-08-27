/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

import net.bzzt.reproduciblebuilds.ReproducibleBuildsPlugin.reproducibleBuildsCheckResolver

scalaVersion := Dependencies.allScalaVersions.head

ThisBuild / versionScheme := Some(VersionScheme.SemVerSpec)
sourceDistName := "apache-pekko"
sourceDistIncubating := false

ThisBuild / reproducibleBuildsCheckResolver := Resolver.ApacheMavenStagingRepo

ThisBuild / pekkoCoreProject := true

enablePlugins(
  UnidocRoot,
  UnidocWithPrValidation,
  NoPublish,
  ScalafixIgnoreFilePlugin,
  JavaFormatterPlugin)
disablePlugins(MimaPlugin)

addCommandAlias("checkCodeStyle", "scalafmtCheckAll; scalafmtSbtCheck; javafmtCheckAll; +headerCheckAll")
addCommandAlias("applyCodeStyle", "+headerCreateAll; scalafmtAll; scalafmtSbt; javafmtAll")

addCommandAlias(
  name = "fixall",
  value = ";scalafixEnable; scalafixAll; scalafmtAll; test:compile; multi-jvm:compile; reload")

addCommandAlias(name = "sortImports", value = ";scalafixEnable; scalafixAll SortImports; scalafmtAll")

import PekkoBuild._
import com.typesafe.tools.mima.plugin.MimaPlugin
import sbt.Keys.{ initialCommands, parallelExecution }
import spray.boilerplate.BoilerplatePlugin

initialize := {
  // Load system properties from a file to make configuration from Jenkins easier
  loadSystemProperties("project/pekko-build.properties")
  initialize.value
}

shellPrompt := { s =>
  Project.extract(s).currentProject.id + " > "
}
resolverSettings

// When this is updated the set of modules in ActorSystem.allModules should also be updated
lazy val userProjects: Seq[ProjectReference] = List[ProjectReference](
  actor,
  actorTestkitTyped,
  actorTyped,
  cluster,
  clusterMetrics,
  clusterSharding,
  clusterShardingTyped,
  clusterTools,
  clusterTyped,
  coordination,
  discovery,
  distributedData,
  jackson,
  multiNodeTestkit,
  osgi,
  persistence,
  persistenceQuery,
  persistenceTyped,
  persistenceTestkit,
  protobufV3,
  pki,
  remote,
  slf4j,
  stream,
  streamTestkit,
  streamTyped,
  testkit)

lazy val aggregatedProjects: Seq[ProjectReference] = userProjects ++ List[ProjectReference](
  actorTests,
  actorTypedTests,
  benchJmh,
  docs,
  billOfMaterials,
  persistenceShared,
  persistenceTck,
  persistenceTypedTests,
  remoteTests,
  streamTests,
  streamTypedTests,
  streamTestsTck)

lazy val root = Project(id = "pekko", base = file("."))
  .aggregate(aggregatedProjects: _*)
  .settings(
    name := "pekko-root")
  .settings(rootSettings: _*)
  .settings(
    UnidocRoot.autoImport.unidocRootIgnoreProjects := Seq(
      remoteTests,
      benchJmh,
      protobufV3,
      pekkoScalaNightly,
      docs,
      serialversionRemoverPlugin))
  .settings(
    Compile / headerCreate / unmanagedSources := (baseDirectory.value / "project").**("*.scala").get)
  .settings(PekkoBuild.welcomeSettings)
  .enablePlugins(CopyrightHeaderForBuild)

lazy val actor = pekkoModule("actor")
  .settings(Dependencies.actor)
  .settings(OSGi.actor)
  .settings(AutomaticModuleName.settings("pekko.actor"))
  .settings(AddMetaInfLicenseFiles.actorSettings)
  .settings(VersionGenerator.settings)
  .settings(serialversionRemoverPluginSettings)
  .enablePlugins(BoilerplatePlugin, SbtOsgi, Jdk9)

lazy val actorTests = pekkoModule("actor-tests")
  .configs(Jdk9.TestJdk9)
  .dependsOn(testkit % "compile->compile;test->test", actor)
  .settings(Dependencies.actorTests)
  .enablePlugins(NoPublish, Jdk9)
  .disablePlugins(MimaPlugin)

lazy val pekkoScalaNightly = pekkoModule("scala-nightly")
  .aggregate(aggregatedProjects: _*)
  .disablePlugins(MimaPlugin)
  .disablePlugins(ValidatePullRequest, MimaPlugin)

lazy val benchJmh = pekkoModule("bench-jmh")
  .enablePlugins(Jdk9)
  .dependsOn(Seq(actor, actorTyped, stream, streamTestkit, persistence, distributedData, jackson, testkit).map(
    _ % "compile->compile;compile->test"): _*)
  .settings(Dependencies.benchJmh)
  .settings(javacOptions += "-parameters") // for Jackson
  .enablePlugins(JmhPlugin, ScaladocNoVerificationOfDiagrams, NoPublish)
  .disablePlugins(MimaPlugin, ValidatePullRequest)

lazy val cluster = pekkoModule("cluster")
  .dependsOn(
    remote,
    coordination % "compile->compile;test->test",
    remoteTests % "test->test",
    testkit % "test->test",
    jackson % "test->test")
  .settings(Dependencies.cluster)
  .settings(AutomaticModuleName.settings("pekko.cluster"))
  .settings(AddMetaInfLicenseFiles.clusterSettings)
  .settings(OSGi.cluster)
  .settings(Protobuf.settings)
  .settings(Test / parallelExecution := false)
  .enablePlugins(MultiNodeScalaTest, SbtOsgi)

lazy val clusterMetrics = pekkoModule("cluster-metrics")
  .dependsOn(
    cluster % "compile->compile;test->test;multi-jvm->multi-jvm",
    slf4j % "test->compile",
    jackson % "test->test")
  .settings(OSGi.clusterMetrics)
  .settings(Dependencies.clusterMetrics)
  .settings(AutomaticModuleName.settings("pekko.cluster.metrics"))
  .settings(Protobuf.settings)
  .settings(SigarLoader.sigarSettings)
  .settings(Test / parallelExecution := false)
  .enablePlugins(MultiNodeScalaTest, SbtOsgi)

lazy val clusterSharding = pekkoModule("cluster-sharding")
// TODO pekko-persistence dependency should be provided in pom.xml artifact.
//      If I only use "provided" here it works, but then we can't run tests.
//      Scope "test" is alright in the pom.xml, but would have been nicer with
//      provided.
  .dependsOn(
    cluster % "compile->compile;test->test;multi-jvm->multi-jvm",
    distributedData,
    persistence % "compile->compile",
    clusterTools % "compile->compile;test->test",
    jackson % "test->test")
  .settings(Dependencies.clusterSharding)
  .settings(AutomaticModuleName.settings("pekko.cluster.sharding"))
  .settings(OSGi.clusterSharding)
  .settings(Protobuf.settings)
  .settings(PekkoDependWalker.jdk9CompileCheckSetting)
  .enablePlugins(MultiNode, ScaladocNoVerificationOfDiagrams, Jdk9, DependWalkerPlugin, SbtOsgi)

lazy val clusterTools = pekkoModule("cluster-tools")
  .dependsOn(
    cluster % "compile->compile;test->test;multi-jvm->multi-jvm",
    coordination % "compile->compile;test->test",
    jackson % "test->test")
  .settings(Dependencies.clusterTools)
  .settings(AutomaticModuleName.settings("pekko.cluster.tools"))
  .settings(OSGi.clusterTools)
  .settings(Protobuf.settings)
  .enablePlugins(MultiNode, ScaladocNoVerificationOfDiagrams, SbtOsgi)

lazy val distributedData = pekkoModule("distributed-data")
  .dependsOn(cluster % "compile->compile;test->test;multi-jvm->multi-jvm", jackson % "test->test")
  .settings(Dependencies.distributedData)
  .settings(AutomaticModuleName.settings("pekko.cluster.ddata"))
  .settings(AddMetaInfLicenseFiles.distributedDataSettings)
  .settings(OSGi.distributedData)
  .settings(Protobuf.settings)
  .enablePlugins(MultiNodeScalaTest, SbtOsgi)

lazy val docs = pekkoModule("docs")
  .configs(Jdk9.TestJdk9)
  .dependsOn(
    actor,
    cluster,
    clusterMetrics,
    slf4j,
    osgi,
    persistenceTck,
    persistenceQuery,
    distributedData,
    stream,
    stream % "TestJdk9->CompileJdk9",
    actorTyped,
    clusterTools % "compile->compile;test->test",
    clusterSharding % "compile->compile;test->test",
    discovery % "compile->compile;test->test",
    testkit % "compile->compile;test->test",
    remote % "compile->compile;test->test",
    persistence % "compile->compile;test->test",
    actorTyped % "compile->compile;test->test",
    persistenceTyped % "compile->compile;test->test",
    clusterTyped % "compile->compile;test->test",
    clusterShardingTyped % "compile->compile;test->test",
    actorTypedTests % "compile->compile;test->test",
    streamTestkit % "compile->compile;test->test",
    persistenceTestkit % "compile->compile;test->test")
  .settings(Dependencies.docs)
  .settings(PekkoDisciplinePlugin.docs)
  .settings(Paradox.settings)
  .settings(javacOptions += "-parameters") // for Jackson
  .enablePlugins(
    ParadoxPlugin,
    PekkoParadoxPlugin,
    NoPublish,
    ParadoxBrowse,
    ProjectIndexGenerator,
    ScaladocNoVerificationOfDiagrams,
    StreamOperatorsIndexGenerator,
    Jdk9)
  .disablePlugins(MimaPlugin)
  .disablePlugins((if (ScalafixSupport.fixTestScope) Nil else Seq(ScalafixPlugin)): _*)

lazy val jackson = pekkoModule("serialization-jackson")
  .dependsOn(
    actor,
    actorTyped % "optional->compile",
    stream % "optional->compile",
    actorTests % "test->test",
    testkit % "test->test")
  .settings(Dependencies.jackson)
  .settings(AutomaticModuleName.settings("pekko.serialization.jackson"))
  .settings(OSGi.jackson)
  .settings(javacOptions += "-parameters")
  .enablePlugins(ScaladocNoVerificationOfDiagrams, SbtOsgi)

lazy val multiNodeTestkit = pekkoModule("multi-node-testkit")
  .dependsOn(remote, testkit)
  .settings(Dependencies.multiNodeTestkit)
  .settings(Protobuf.settings)
  .settings(AutomaticModuleName.settings("pekko.remote.testkit"))
  .settings(PekkoBuild.mayChangeSettings)

lazy val osgi = pekkoModule("osgi")
  .dependsOn(actor)
  .settings(Dependencies.osgi)
  .settings(AutomaticModuleName.settings("pekko.osgi"))
  .settings(OSGi.osgi)
  .settings(Test / parallelExecution := false)
  .enablePlugins(SbtOsgi)

lazy val persistence = pekkoModule("persistence")
  .dependsOn(actor, stream, testkit % "test->test")
  .settings(Dependencies.persistence)
  .settings(AutomaticModuleName.settings("pekko.persistence"))
  .settings(OSGi.persistence)
  .settings(Protobuf.settings)
  .settings(Test / fork := true)
  .enablePlugins(SbtOsgi)

lazy val persistenceQuery = pekkoModule("persistence-query")
  .dependsOn(
    stream,
    persistence % "compile->compile;test->test",
    remote % "provided",
    protobufV3,
    streamTestkit % Test)
  .settings(Dependencies.persistenceQuery)
  .settings(AutomaticModuleName.settings("pekko.persistence.query"))
  .settings(OSGi.persistenceQuery)
  .settings(Protobuf.settings)
  // To be able to import ContainerFormats.proto
  .settings(Protobuf.importPath := Some(baseDirectory.value / ".." / "remote" / "src" / "main" / "protobuf"))
  .settings(Test / fork := true)
  .enablePlugins(ScaladocNoVerificationOfDiagrams, SbtOsgi)

lazy val persistenceShared = pekkoModule("persistence-shared")
  .dependsOn(persistence % "test->test", testkit % "test->test", remote % Test)
  .settings(Dependencies.persistenceShared)
  .settings(AutomaticModuleName.settings("pekko.persistence.shared"))
  .settings(Test / fork := true)
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin)

lazy val persistenceTck = pekkoModule("persistence-tck")
  .dependsOn(persistence % "compile->compile;test->test", testkit % "compile->compile;test->test")
  .settings(Dependencies.persistenceTck)
  .settings(AutomaticModuleName.settings("pekko.persistence.tck"))
  // .settings(OSGi.persistenceTck) TODO: we do need to export this as OSGi bundle too?
  .settings(Test / fork := true)
  .disablePlugins(MimaPlugin)

lazy val persistenceTestkit = pekkoModule("persistence-testkit")
  .dependsOn(
    persistenceTyped % "compile->compile;provided->provided;test->test",
    testkit % "compile->compile;test->test",
    actorTestkitTyped,
    persistenceTck % Test)
  .settings(Dependencies.persistenceTestKit)
  .settings(AutomaticModuleName.settings("pekko.persistence.testkit"))
  .disablePlugins(MimaPlugin)

lazy val persistenceTypedTests = pekkoModule("persistence-typed-tests")
  .dependsOn(
    persistenceTyped,
    persistenceTestkit % Test,
    actorTestkitTyped % Test,
    persistence % "test->test", // for SteppingInMemJournal
    jackson % "test->test")
  .settings(PekkoBuild.mayChangeSettings)
  .settings(Dependencies.persistenceTypedTests)
  .settings(javacOptions += "-parameters") // for Jackson
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublish)

lazy val protobufV3 = pekkoModule("protobuf-v3")
  .settings(OSGi.protobufV3)
  .settings(AutomaticModuleName.settings("pekko.protobuf.v3"))
  .settings(AddMetaInfLicenseFiles.protobufV3Settings)
  .enablePlugins(ScaladocNoVerificationOfDiagrams, SbtOsgi)
  .disablePlugins(MimaPlugin)
  .settings(
    libraryDependencies += Dependencies.Compile.Provided.protobufRuntime,
    assembly / assemblyShadeRules := Seq(
      ShadeRule
        .rename("com.google.protobuf.**" -> "org.apache.pekko.protobufv3.internal.@1")
        // https://github.com/sbt/sbt-assembly/issues/400
        .inLibrary(Dependencies.Compile.Provided.protobufRuntime)
        .inProject),
    assembly / assemblyOption := (assembly / assemblyOption).value.withIncludeScala(false).withIncludeBin(true),
    autoScalaLibrary := false, // do not include scala dependency in pom
    exportJars := true, // in dependent projects, use assembled and shaded jar
    makePomConfiguration := makePomConfiguration.value
      .withConfigurations(Vector(Compile)), // prevent original dependency to be added to pom as runtime dep
    Compile / packageBin / packagedArtifact := Scoped.mkTuple2(
      (Compile / packageBin / artifact).value,
      ReproducibleBuildsPlugin.postProcessJar(OsgiKeys.bundle.value)),
    Compile / packageBin := Def.taskDyn {
      val store = streams.value.cacheStoreFactory.make("shaded-output")
      val uberJarLocation = (assembly / assemblyOutputPath).value
      val tracker = Tracked.outputChanged(store) { (changed: Boolean, file: File) =>
        if (changed) {
          Def.task {
            val uberJar = (Compile / assembly).value
            ReproducibleBuildsPlugin.postProcessJar(uberJar)
          }
        } else Def.task { file }
      }
      tracker(() => uberJarLocation)
    }.value,
    // Prevent cyclic task dependencies, see https://github.com/sbt/sbt-assembly/issues/365 by
    // redefining the fullClasspath with just what we need to avoid the cyclic task dependency
    assembly / fullClasspath := (Runtime / managedClasspath).value ++ (Compile / products).value.map(Attributed.blank),
    assembly / test := {}, // assembly runs tests for unknown reason which introduces another cyclic dependency to packageBin via exportedJars
    description := "Apache Pekko Protobuf V3 is a shaded version of the protobuf runtime. Original POM: https://github.com/protocolbuffers/protobuf/blob/v3.9.0/java/pom.xml")

lazy val pki =
  pekkoModule("pki")
    .dependsOn(actor) // this dependency only exists for "@ApiMayChange"
    .settings(Dependencies.pki)
    .settings(AutomaticModuleName.settings("pekko.pki"))
    .disablePlugins(MimaPlugin)

lazy val remote =
  pekkoModule("remote")
    .dependsOn(
      actor,
      stream,
      pki,
      actorTests % "test->test",
      testkit % "test->test",
      streamTestkit % Test,
      jackson % "test->test")
    .settings(Dependencies.remote)
    .settings(AutomaticModuleName.settings("pekko.remote"))
    .settings(AddMetaInfLicenseFiles.remoteSettings)
    .settings(OSGi.remote)
    .settings(Protobuf.settings)
    .settings(Test / parallelExecution := false)
    .settings(serialversionRemoverPluginSettings)
    .settings(PekkoDependWalker.jdk9CompileCheckSetting)
    .enablePlugins(Jdk9, DependWalkerPlugin, SbtOsgi)

lazy val remoteTests = pekkoModule("remote-tests")
  .dependsOn(
    actorTests % "test->test",
    remote % "compile->CompileJdk9;test->test",
    streamTestkit % Test,
    multiNodeTestkit,
    jackson % "test->test")
  .settings(Dependencies.remoteTests)
  .settings(Protobuf.settings)
  .settings(Test / parallelExecution := false)
  .enablePlugins(MultiNodeScalaTest, NoPublish)
  .disablePlugins(MimaPlugin)

lazy val slf4j = pekkoModule("slf4j")
  .dependsOn(actor, testkit % "test->test")
  .settings(Dependencies.slf4j)
  .settings(AutomaticModuleName.settings("pekko.slf4j"))
  .settings(OSGi.slf4j)
  .enablePlugins(SbtOsgi)

lazy val stream = pekkoModule("stream")
  .dependsOn(actor, protobufV3)
  .settings(Dependencies.stream)
  .settings(AutomaticModuleName.settings("pekko.stream"))
  .settings(OSGi.stream)
  .settings(Protobuf.settings)
  .settings(VerifyJDK9Classes.settings)
  .settings(PekkoDependWalker.jdk9CompileCheckSetting)
  .enablePlugins(BoilerplatePlugin, Jdk9, DependWalkerPlugin, SbtOsgi)

lazy val streamTestkit = pekkoModule("stream-testkit")
  .dependsOn(stream, testkit % "compile->compile;test->test")
  .settings(Dependencies.streamTestkit)
  .settings(AutomaticModuleName.settings("pekko.stream.testkit"))
  .settings(OSGi.streamTestkit)
  .enablePlugins(SbtOsgi)

lazy val streamTests = pekkoModule("stream-tests")
  .configs(Jdk9.TestJdk9)
  .dependsOn(streamTestkit % "test->test", remote % "test->test", stream % "TestJdk9->CompileJdk9")
  .settings(Dependencies.streamTests)
  .enablePlugins(NoPublish, Jdk9)
  .disablePlugins(MimaPlugin)

lazy val streamTestsTck = pekkoModule("stream-tests-tck")
  .dependsOn(streamTestkit % "test->test", stream)
  .settings(Dependencies.streamTestsTck)
  .settings(
    // These TCK tests are using System.gc(), which
    // is causing long GC pauses when running with G1 on
    // the CI build servers. Therefore we fork these tests
    // to run with small heap without G1.
    Test / fork := true)
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin)

lazy val testkit = pekkoModule("testkit")
  .dependsOn(actor)
  .settings(Dependencies.testkit)
  .settings(AutomaticModuleName.settings("pekko.actor.testkit"))
  .settings(OSGi.testkit)
  .settings(initialCommands += "import org.apache.pekko.testkit._")
  .enablePlugins(SbtOsgi)

lazy val actorTyped = pekkoModule("actor-typed")
  .dependsOn(actor, slf4j)
  .settings(AutomaticModuleName.settings("pekko.actor.typed"))
  .settings(Dependencies.actorTyped)
  .settings(OSGi.actorTyped)
  .settings(initialCommands :=
    """
      import org.apache.pekko

      import pekko.actor.typed._
      import pekko.actor.typed.scaladsl.Behaviors
      import pekko.util.Timeout

      import scala.concurrent._
      import scala.concurrent.duration._
      import scala.language.postfixOps

      implicit val timeout = Timeout(5 seconds)
    """)
  .settings(PekkoDependWalker.jdk9CompileCheckSetting)
  .enablePlugins(Jdk9, DependWalkerPlugin, SbtOsgi)

lazy val persistenceTyped = pekkoModule("persistence-typed")
  .dependsOn(
    actorTyped,
    streamTyped,
    remote,
    persistence % "compile->compile;test->test",
    persistenceQuery,
    actorTestkitTyped % "test->test",
    clusterTyped % "test->test",
    actorTestkitTyped % "test->test",
    jackson % "test->test")
  .settings(javacOptions += "-parameters") // for Jackson
  .settings(Dependencies.persistenceShared)
  .settings(AutomaticModuleName.settings("pekko.persistence.typed"))
  .settings(AddMetaInfLicenseFiles.persistenceTypedSettings)
  .settings(Protobuf.settings)
  // To be able to import ContainerFormats.proto
  .settings(Protobuf.importPath := Some(baseDirectory.value / ".." / "remote" / "src" / "main" / "protobuf"))
  .settings(OSGi.persistenceTyped)
  .enablePlugins(SbtOsgi)

lazy val clusterTyped = pekkoModule("cluster-typed")
  .dependsOn(
    actorTyped,
    cluster % "compile->compile;test->test;multi-jvm->multi-jvm",
    clusterTools,
    distributedData,
    actorTestkitTyped % "test->test",
    actorTypedTests % "test->test",
    remoteTests % "test->test",
    jackson % "test->test")
  .settings(Protobuf.settings)
  // To be able to import ContainerFormats.proto
  .settings(Protobuf.importPath := Some(baseDirectory.value / ".." / "remote" / "src" / "main" / "protobuf"))
  .settings(AutomaticModuleName.settings("pekko.cluster.typed"))
  .settings(Protobuf.settings)
  // To be able to import ContainerFormats.proto
  .settings(Protobuf.importPath := Some(baseDirectory.value / ".." / "remote" / "src" / "main" / "protobuf"))
  .enablePlugins(MultiNodeScalaTest)

lazy val clusterShardingTyped = pekkoModule("cluster-sharding-typed")
  .dependsOn(
    actorTyped % "compile->CompileJdk9",
    clusterTyped % "compile->compile;test->test;multi-jvm->multi-jvm",
    clusterSharding % "compile->compile;compile->CompileJdk9;multi-jvm->multi-jvm",
    actorTestkitTyped % "test->test",
    actorTypedTests % "test->test",
    persistenceTyped % "optional->compile;test->test",
    persistenceTestkit % "test->test",
    remote % "compile->CompileJdk9;test->test",
    remoteTests % "test->test",
    remoteTests % "test->test;multi-jvm->multi-jvm",
    jackson % "test->test")
  .settings(javacOptions += "-parameters") // for Jackson
  .settings(AutomaticModuleName.settings("pekko.cluster.sharding.typed"))
  // To be able to import ContainerFormats.proto
  .settings(Protobuf.settings)
  .settings(Protobuf.importPath := Some(baseDirectory.value / ".." / "remote" / "src" / "main" / "protobuf"))
  .enablePlugins(MultiNodeScalaTest)

lazy val streamTyped = pekkoModule("stream-typed")
  .dependsOn(
    actorTyped,
    stream,
    streamTestkit % "test->test",
    actorTestkitTyped % "test->test",
    actorTypedTests % "test->test")
  .settings(AutomaticModuleName.settings("pekko.stream.typed"))
  .enablePlugins(ScaladocNoVerificationOfDiagrams)

lazy val streamTypedTests = pekkoModule("stream-typed-tests")
  .dependsOn(streamTestkit % "test->test", streamTyped)
  .settings(Dependencies.streamTests)
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin)

lazy val actorTestkitTyped = pekkoModule("actor-testkit-typed")
  .dependsOn(actorTyped, slf4j, testkit % "compile->compile;test->test")
  .settings(AutomaticModuleName.settings("pekko.actor.testkit.typed"))
  .settings(Dependencies.actorTestkitTyped)

lazy val actorTypedTests = pekkoModule("actor-typed-tests")
  .dependsOn(actorTyped % "compile->CompileJdk9", actorTestkitTyped % "compile->compile;test->test", actor)
  .settings(PekkoBuild.mayChangeSettings)
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublish)

lazy val discovery = pekkoModule("discovery")
  .dependsOn(actor, testkit % "test->test", actorTests % "test->test")
  .settings(Dependencies.discovery)
  .settings(AutomaticModuleName.settings("pekko.discovery"))
  .settings(OSGi.discovery)
  .enablePlugins(SbtOsgi)

lazy val coordination = pekkoModule("coordination")
  .dependsOn(actor, testkit % "test->test", actorTests % "test->test")
  .settings(Dependencies.coordination)
  .settings(AutomaticModuleName.settings("pekko.coordination"))
  .settings(OSGi.coordination)
  .enablePlugins(SbtOsgi)

lazy val billOfMaterials = Project("bill-of-materials", file("bill-of-materials"))
  .enablePlugins(BillOfMaterialsPlugin)
  .disablePlugins(MimaPlugin, PekkoDisciplinePlugin)
  // buildSettings and defaultSettings configure organization name, licenses, etc...
  .settings(PekkoBuild.defaultSettings)
  .settings(
    name := "pekko-bom",
    bomIncludeProjects := userProjects,
    description := s"${description.value} (depending on Scala ${CrossVersion.binaryScalaVersion(scalaVersion.value)})")

lazy val serialversionRemoverPlugin =
  Project(id = "serialVersionRemoverPlugin", base = file("plugins/serialversion-remover-plugin")).settings(
    scalaVersion := Dependencies.scala3Version,
    libraryDependencies += ("org.scala-lang" %% "scala3-compiler" % Dependencies.scala3Version),
    Compile / doc / sources := Nil,
    Compile / publishArtifact := false)

lazy val serialversionRemoverPluginSettings = Seq(
  Compile / scalacOptions ++= (
    if (scalaVersion.value.startsWith("3."))
      Seq("-Xplugin:" + (serialversionRemoverPlugin / Compile / Keys.`package`).value.getAbsolutePath.toString)
    else Nil
  ))

def pekkoModule(moduleName: String): Project =
  Project(id = moduleName, base = file(moduleName))
    .enablePlugins(ReproducibleBuildsPlugin)
    .disablePlugins(WelcomePlugin)
    .settings(PekkoBuild.defaultSettings)
    .settings(
      name := s"pekko-$moduleName")
    .enablePlugins(BootstrapGenjavadoc)

/* Command aliases one can run locally against a module
  - where three or more tasks should be checked for faster turnaround
  - to avoid another push and CI cycle should mima or paradox fail.
  - the assumption is the user has already run tests, hence the test:compile. */
def commandValue(p: Project, externalTest: Option[Project] = None) = {
  val test = externalTest.getOrElse(p)
  val optionalMima = if (p.id.endsWith("-typed")) "" else s";${p.id}/mimaReportBinaryIssues"
  val optionalExternalTestFormat = externalTest.map(t => s";${t.id}/scalafmtAll").getOrElse("")
  s";${p.id}/scalafmtAll$optionalExternalTestFormat;${test.id}/test:compile$optionalMima;${docs.id}/paradox;${test.id}:validateCompile"
}
addCommandAlias("allActor", commandValue(actor, Some(actorTests)))
addCommandAlias("allRemote", commandValue(remote, Some(remoteTests)))
addCommandAlias("allClusterCore", commandValue(cluster))
addCommandAlias("allClusterMetrics", commandValue(clusterMetrics))
addCommandAlias("allClusterSharding", commandValue(clusterSharding))
addCommandAlias("allClusterTools", commandValue(clusterTools))
addCommandAlias(
  "allCluster",
  Seq(commandValue(cluster), commandValue(distributedData), commandValue(clusterSharding),
    commandValue(clusterTools)).mkString)
addCommandAlias("allCoordination", commandValue(coordination))
addCommandAlias("allDistributedData", commandValue(distributedData))
addCommandAlias("allPersistence", commandValue(persistence))
addCommandAlias("allStream", commandValue(stream, Some(streamTests)))
addCommandAlias("allDiscovery", commandValue(discovery))
addCommandAlias(
  "allTyped",
  Seq(
    commandValue(actorTyped, Some(actorTypedTests)),
    commandValue(actorTestkitTyped),
    commandValue(clusterTyped),
    commandValue(clusterShardingTyped),
    commandValue(persistenceTyped),
    commandValue(streamTyped)).mkString)
