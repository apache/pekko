/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import org.apache.pekko.testkit.PekkoSpec

class ManifestInfoSpec extends PekkoSpec {
  "ManifestInfo" should {
    "produce a clear message" in {
      val versions = Map(
        "pekko-actor" -> new ManifestInfo.Version("1.0.2"),
        "pekko-persistence" -> new ManifestInfo.Version("1.0.1"),
        "pekko-cluster" -> new ManifestInfo.Version("1.0.1"),
        "unrelated" -> new ManifestInfo.Version("1.0.1"))
      val allModules = List("pekko-actor", "pekko-persistence", "pekko-cluster")
      ManifestInfo.checkSameVersion("Pekko", allModules, versions) shouldBe Some(
        "You are using version 1.0.2 of Pekko, but it appears you (perhaps indirectly) also depend on older versions of related artifacts. " +
        "You can solve this by adding an explicit dependency on version 1.0.2 of the [pekko-persistence, pekko-cluster] artifacts to your project. " +
        "Here's a complete collection of detected artifacts: (1.0.1, [pekko-cluster, pekko-persistence]), (1.0.2, [pekko-actor]). " +
        "See also: https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed")
    }

    "support dynver" in {
      val versions = Map(
        "pekko-actor" -> new ManifestInfo.Version("1.0.2"),
        "pekko-persistence" -> new ManifestInfo.Version("1.0.2+10-abababef"))
      val allModules = List("pekko-actor", "pekko-persistence")
      ManifestInfo.checkSameVersion("Pekko", allModules, versions) shouldBe Some(
        "You are using version 1.0.2+10-abababef of Pekko, but it appears you (perhaps indirectly) also depend on older versions of related artifacts. " +
        "You can solve this by adding an explicit dependency on version 1.0.2+10-abababef of the [pekko-actor] artifacts to your project. " +
        "Here's a complete collection of detected artifacts: (1.0.2, [pekko-actor]), (1.0.2+10-abababef, [pekko-persistence]). " +
        "See also: https://pekko.apache.org/docs/pekko/current/common/binary-compatibility-rules.html#mixed-versioning-is-not-allowed")
    }
  }
}
