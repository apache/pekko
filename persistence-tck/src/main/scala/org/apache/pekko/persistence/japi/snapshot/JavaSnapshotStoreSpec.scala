/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.japi.snapshot

import scala.collection.immutable

import com.typesafe.config.Config
import org.apache.pekko
import pekko.persistence.CapabilityFlag
import pekko.persistence.snapshot.SnapshotStoreSpec
import org.scalatest.{ Args, ConfigMap, Filter, Status, Suite, TestData }

/**
 * JAVA API
 *
 * This spec aims to verify custom akka-persistence [[pekko.persistence.snapshot.SnapshotStore]] implementations.
 * Plugin authors are highly encouraged to include it in their plugin's test suites.
 *
 * In case your snapshot-store plugin needs some kind of setup or teardown, override the `beforeAll` or `afterAll`
 * methods (don't forget to call `super` in your overridden methods).
 *
 * @see [[pekko.persistence.snapshot.SnapshotStoreSpec]]
 */
class JavaSnapshotStoreSpec(config: Config) extends SnapshotStoreSpec(config) {
  override protected def supportsSerialization: CapabilityFlag = CapabilityFlag.on()

  override def runTests(testName: Option[String], args: Args): Status =
    super.runTests(testName, args)

  override def runTest(testName: String, args: Args): Status =
    super.runTest(testName, args)

  override def run(testName: Option[String], args: Args): Status =
    super.run(testName, args)

  override def testDataFor(testName: String, theConfigMap: ConfigMap): TestData =
    super.testDataFor(testName, theConfigMap)

  override def testNames: Set[String] =
    super.testNames

  override def tags: Map[String, Set[String]] =
    super.tags

  override def rerunner: Option[String] =
    super.rerunner

  override def expectedTestCount(filter: Filter): Int =
    super.expectedTestCount(filter)

  override def suiteId: String =
    super.suiteId

  override def suiteName: String =
    super.suiteName

  override def runNestedSuites(args: Args): Status =
    super.runNestedSuites(args)

  override def nestedSuites: immutable.IndexedSeq[Suite] =
    super.nestedSuites
}
