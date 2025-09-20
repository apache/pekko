/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.cluster.typed.internal

import scala.concurrent.duration._

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.TestProbe
import pekko.actor.typed.receptionist.Receptionist
import pekko.actor.typed.receptionist.ServiceKey
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.MultiNodeClusterSpec
import pekko.cluster.typed.MultiDcClusterSingletonSpecConfig.first
import pekko.cluster.typed.MultiDcClusterSingletonSpecConfig.second
import pekko.cluster.typed.MultiDcClusterSingletonSpecConfig.third
import pekko.cluster.typed.MultiNodeTypedClusterSpec
import pekko.remote.testkit.MultiNodeConfig
import pekko.remote.testkit.MultiNodeSpec
import pekko.remote.transport.ThrottlerTransportAdapter.Direction

import com.typesafe.config.ConfigFactory

object ClusterReceptionistUnreachabilitySpecConfig extends MultiNodeConfig {
  val first = role("first")
  val second = role("second")
  val third = role("third")

  commonConfig(ConfigFactory.parseString("""
        pekko.loglevel = INFO
      """).withFallback(MultiNodeClusterSpec.clusterConfig))

  testTransport(on = true)
}

object ClusterReceptionistUnreachabilitySpec {
  val MyServiceKey = ServiceKey[String]("my-service")
}

class ClusterReceptionistUnreachabilityMultiJvmNode1 extends ClusterReceptionistUnreachabilitySpec
class ClusterReceptionistUnreachabilityMultiJvmNode2 extends ClusterReceptionistUnreachabilitySpec
class ClusterReceptionistUnreachabilityMultiJvmNode3 extends ClusterReceptionistUnreachabilitySpec

abstract class ClusterReceptionistUnreachabilitySpec
    extends MultiNodeSpec(ClusterReceptionistUnreachabilitySpecConfig)
    with MultiNodeTypedClusterSpec {

  import ClusterReceptionistUnreachabilitySpec._

  val probe = TestProbe[AnyRef]()
  val receptionistProbe = TestProbe[AnyRef]()

  "The clustered receptionist" must {

    "subscribe to the receptionist" in {
      typedSystem.receptionist ! Receptionist.Subscribe(MyServiceKey, receptionistProbe.ref)
      val listing = receptionistProbe.expectMessageType[Receptionist.Listing]
      listing.serviceInstances(MyServiceKey) should ===(Set.empty)
      listing.allServiceInstances(MyServiceKey) should ===(Set.empty)
      listing.servicesWereAddedOrRemoved should ===(true)
      enterBarrier("all subscribed")
    }

    "form a cluster" in {
      formCluster(first, second, third)
      enterBarrier("cluster started")
    }

    "register a service" in {
      val localServiceRef = spawn(Behaviors.receiveMessage[String] {
          case msg =>
            probe.ref ! msg
            Behaviors.same
        }, "my-service")
      typedSystem.receptionist ! Receptionist.Register(MyServiceKey, localServiceRef)
      enterBarrier("all registered")
    }

    "see registered services" in {
      awaitAssert({
          val listing = receptionistProbe.expectMessageType[Receptionist.Listing]
          listing.serviceInstances(MyServiceKey) should have size 3
          listing.allServiceInstances(MyServiceKey) should have size 3
          listing.servicesWereAddedOrRemoved should ===(true)
        }, 20.seconds)

      enterBarrier("all seen registered")
    }

    "remove unreachable from listing" in {
      // make second unreachable
      runOn(first) {
        testConductor.blackhole(first, second, Direction.Both).await
        testConductor.blackhole(third, second, Direction.Both).await
      }

      runOn(first, third) {
        // assert service on 2 is not in listing but in all and flag is false
        awaitAssert({
            val listing = receptionistProbe.expectMessageType[Receptionist.Listing]
            listing.serviceInstances(MyServiceKey) should have size 2
            listing.allServiceInstances(MyServiceKey) should have size 3
            listing.servicesWereAddedOrRemoved should ===(false)
          }, 20.seconds)
      }
      runOn(second) {
        // assert service on 1 and 3 is not in listing but in all and flag is false
        awaitAssert({
            val listing = receptionistProbe.expectMessageType[Receptionist.Listing]
            listing.serviceInstances(MyServiceKey) should have size 1
            listing.allServiceInstances(MyServiceKey) should have size 3
            listing.servicesWereAddedOrRemoved should ===(false)
          }, 20.seconds)
      }
      enterBarrier("all seen unreachable")
    }

    "add again-reachable to list again" in {
      // make second unreachable
      runOn(first) {
        testConductor.passThrough(first, second, Direction.Both).await
        testConductor.passThrough(third, second, Direction.Both).await
      }

      awaitAssert {
        val listing = receptionistProbe.expectMessageType[Receptionist.Listing]
        listing.serviceInstances(MyServiceKey) should have size 3
        listing.allServiceInstances(MyServiceKey) should have size 3
        listing.servicesWereAddedOrRemoved should ===(false)
      }
      enterBarrier("all seen reachable-again")
    }

  }

}
