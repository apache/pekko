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

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.coordination.lease.scaladsl.Lease
import pekko.pattern.AskTimeoutException
import pekko.testkit.PekkoSpec

import com.typesafe.config.{ Config, ConfigFactory }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class LeaseHealthCheckSpec
    extends PekkoSpec(LeaseHealthCheckSpec.config)
    with ScalaFutures
    with AnyWordSpecLike
    with Matchers {

  import LeaseHealthCheckSpec._

  override implicit val patience: PatienceConfig = PatienceConfig(5.seconds)

  private def newTestLease(): MockLease with MockLeaseState =
    new MockLease(mockLeaseSettings) with MockLeaseState

  private def healthCheckWith(testLease: Lease): LeaseHealthCheck =
    new LeaseHealthCheck(system, "mock-lease") {
      override protected val lease: Lease = testLease
    }

  "LeaseHealthCheckSpec" should {
    "return true and release lease on successful acquisition" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.successful(true)

      healthCheckWith(lease).check().futureValue shouldEqual true
      lease.releaseCalled shouldEqual true
    }

    "not call acquire again after healthCheckPassed is true" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.successful(true)

      val healthcheck = healthCheckWith(lease)
      healthcheck.check().futureValue shouldEqual true
      lease.acquireCalls shouldEqual 1

      healthcheck.check().futureValue shouldEqual true
      lease.acquireCalls shouldEqual 1
    }

    "return true on lease conflict" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.successful(false)
      healthCheckWith(lease).check().futureValue shouldEqual true
    }

    "return false on LeaseException" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.failed(new LeaseException("API error"))
      healthCheckWith(lease).check().futureValue shouldEqual false
    }

    "return false on AskTimeoutException" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.failed(new AskTimeoutException("timeout"))
      healthCheckWith(lease).check().futureValue shouldEqual false
    }

    "return false on generic Exception" in {
      val lease = newTestLease()
      lease.nextAcquire = () => Future.failed(new RuntimeException("generic error"))
      healthCheckWith(lease).check().futureValue shouldEqual false
    }
  }
}

object LeaseHealthCheckSpec {
  val config: Config = ConfigFactory.parseString(s"""
  mock-lease {
    lease-class = "${classOf[LeaseHealthCheckSpec.MockLease].getName}"
    heartbeat-timeout = 100s
    heartbeat-interval = 1s
    lease-operation-timeout = 2s
  }
  """)

  private val mockLeaseSettings = LeaseSettings(config.getConfig("mock-lease"), "test-lease", "test-owner")

  trait MockLeaseState {
    @volatile var nextAcquire: () => Future[Boolean] = () => Future.successful(true)
    @volatile var releaseCalled: Boolean = false
    @volatile var acquireCalls: Int = 0
  }

  class MockLease(settings: LeaseSettings) extends Lease(settings) with MockLeaseState {
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
}
