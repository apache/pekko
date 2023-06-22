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

package docs.persistence.testkit

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.persistence.testkit.{ PersistenceTestKitPlugin, PersistenceTestKitSnapshotPlugin }
import org.apache.pekko.persistence.testkit.scaladsl.{ PersistenceTestKit, SnapshotTestKit }
import com.typesafe.config.ConfigFactory

object TestKitTypedConf {

  // #testkit-typed-conf

  val yourConfiguration = ConfigFactory.defaultApplication()

  val system =
    ActorSystem(??? /*some behavior*/, "test-system", PersistenceTestKitPlugin.config.withFallback(yourConfiguration))

  val testKit = PersistenceTestKit(system)

  // #testkit-typed-conf

}

object SnapshotTypedConf {

  // #snapshot-typed-conf

  val yourConfiguration = ConfigFactory.defaultApplication()

  val system = ActorSystem(
    ??? /*some behavior*/,
    "test-system",
    PersistenceTestKitSnapshotPlugin.config.withFallback(yourConfiguration))

  val testKit = SnapshotTestKit(system)

  // #snapshot-typed-conf

}
