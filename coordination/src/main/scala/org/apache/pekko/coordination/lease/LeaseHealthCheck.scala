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

import java.util.UUID

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.coordination.lease.scaladsl.{ Lease, LeaseProvider }
import pekko.event.Logging
import pekko.pattern.AskTimeoutException

/**
 * Performs a lease health check by attempting to acquire and immediately release a test lease.
 *
 * Once the first check succeeds, subsequent checks return true without contacting the API.
 * This is a quick connectivity test to verify the lease API is accessible.
 *
 * Returns true if:
 * - Lease is successfully acquired (true)
 * - Lease cannot be acquired due to conflict (another owner has it, but the API is reachable) (false)
 *
 * Returns false if:
 * - Any exception occurs (LeaseException, AskTimeoutException, etc.)
 */
class LeaseHealthCheck(system: ActorSystem, leaseProviderName: String) extends (() => Future[Boolean]) {

  private implicit val executionContext: ExecutionContext = system.dispatcher

  private val log = Logging(system, classOf[LeaseHealthCheck])

  @volatile private var healthCheckPassed = false

  private val randomUUIDString = UUID.randomUUID().toString
  private val leaseName = s"lease-$randomUUIDString"
  private val ownerName = s"owner-$randomUUIDString"

  protected val lease: Lease = LeaseProvider(system).getLease(leaseName, leaseProviderName, ownerName)

  override def apply(): Future[Boolean] = check()

  @nowarn("msg=match may not be exhaustive")
  def check(): Future[Boolean] = {
    if (healthCheckPassed) {
      Future.successful(true)
    } else {
      lease.acquire().transform {
        case Success(true) =>
          healthCheckPassed = true
          log.info(s"lease $leaseName from $ownerName returned true")
          lease.release()
          Success(true)
        case Success(false) =>
          log.info(s"lease $leaseName from $ownerName returned false")
          healthCheckPassed = true
          Success(true)
        case Failure(e: LeaseException) =>
          log.warning(s"lease $leaseName from $ownerName returned a LeaseException ${e.getMessage}")
          Success(false)
        case Failure(e: AskTimeoutException) =>
          log.warning(s"lease $leaseName from $ownerName returned an AskTimeoutException ${e.getMessage}")
          Success(false)
        case Failure(e: Exception) =>
          log.warning(s"lease $leaseName from $ownerName returned an Exception ${e.getMessage}")
          Success(false)
      }
    }
  }
}
