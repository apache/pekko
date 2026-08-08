/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.coordination.lease

import com.typesafe.config.{ Config, ConfigFactory }
import org.apache.pekko.coordination.lease.scaladsl.Lease
import org.apache.pekko.pattern.AskTimeoutException
import org.apache.pekko.testkit.PekkoSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.Future
import scala.concurrent.duration._

class LeaseHealthCheckSpec
    extends PekkoSpec(LeaseHealthCheckSpec.config)
    with ScalaFutures
    with AnyWordSpecLike
    with BeforeAndAfterAll
    with Matchers {

  override implicit val patience: PatienceConfig = PatienceConfig(5.seconds)

  "LeaseHealthCheckSpec" should {

    "return true and release lease on successful acquisition" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.successful(true)
      LeaseHealthCheckSpec.releaseCalled = false
      new LeaseHealthCheck(system, "mock-lease").check().futureValue shouldEqual true
      LeaseHealthCheckSpec.releaseCalled shouldEqual true
    }

    "not call acquire again after healthCheckPassed is true" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.successful(true)
      LeaseHealthCheckSpec.acquireCalls = 0
      val healthcheck = new LeaseHealthCheck(system, "mock-lease")
      healthcheck.check().futureValue shouldEqual true
      LeaseHealthCheckSpec.acquireCalls shouldEqual 1

      healthcheck.check().futureValue shouldEqual true
      LeaseHealthCheckSpec.acquireCalls shouldEqual 1
    }

    "return true on lease conflict" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.successful(false)
      new LeaseHealthCheck(system, "mock-lease").check().futureValue shouldEqual true
    }

    "return false on LeaseException" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.failed(new LeaseException("API error"))
      new LeaseHealthCheck(system, "mock-lease").check().futureValue shouldEqual false
    }

    "return false on AskTimeoutException" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.failed(new AskTimeoutException("timeout"))
      new LeaseHealthCheck(system, "mock-lease").check().futureValue shouldEqual false
    }

    "return false on generic Exception" in {
      LeaseHealthCheckSpec.nextAcquire = () => Future.failed(new RuntimeException("generic error"))
      new LeaseHealthCheck(system, "mock-lease").check().futureValue shouldEqual false
    }
  }
}

object LeaseHealthCheckSpec {
  @volatile var nextAcquire: () => Future[Boolean] = () => Future.successful(true)
  @volatile var releaseCalled = false
  @volatile var acquireCalls = 0

  class MockLease(settings: LeaseSettings) extends Lease(settings) {
    override def acquire(): Future[Boolean] = {
      acquireCalls += 1
      nextAcquire()
    }
    override def acquire(callback: Option[Throwable] => Unit): Future[Boolean] = acquire()
    override def release(): Future[Boolean] = {
      releaseCalled = true
      Future.successful(true)
    }
    override def checkLease(): Boolean = true
  }

  val config: Config = ConfigFactory.parseString(s"""
  mock-lease {
    lease-class = "${classOf[LeaseHealthCheckSpec.MockLease].getName}"
    heartbeat-timeout = 100s
    heartbeat-interval = 1s
    lease-operation-timeout = 2s
  }
  """)
}
