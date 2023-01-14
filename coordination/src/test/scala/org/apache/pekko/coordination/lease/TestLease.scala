/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.coordination.lease

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

import scala.concurrent.Future
import scala.concurrent.Promise

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.coordination.lease.scaladsl.Lease
import pekko.event.Logging
import pekko.testkit.TestProbe
import pekko.util.ccompat.JavaConverters._

object TestLeaseExt extends ExtensionId[TestLeaseExt] with ExtensionIdProvider {
  override def get(system: ActorSystem): TestLeaseExt = super.get(system)
  override def get(system: ClassicActorSystemProvider): TestLeaseExt = super.get(system)
  override def lookup = TestLeaseExt
  override def createExtension(system: ExtendedActorSystem): TestLeaseExt = new TestLeaseExt(system)
}

class TestLeaseExt(val system: ExtendedActorSystem) extends Extension {

  private val testLeases = new ConcurrentHashMap[String, TestLease]()

  def getTestLease(name: String): TestLease = {
    val lease = testLeases.get(name)
    if (lease == null)
      throw new IllegalStateException(
        s"Test lease $name has not been set yet. Current leases ${testLeases.keys().asScala.toList}")
    lease
  }

  def setTestLease(name: String, lease: TestLease): Unit =
    testLeases.put(name, lease)

}

object TestLease {
  final case class AcquireReq(owner: String)
  final case class ReleaseReq(owner: String)

  val config = ConfigFactory.parseString(s"""
    test-lease {
      lease-class = ${classOf[TestLease].getName}
    }
    """.stripMargin)
}

class TestLease(settings: LeaseSettings, system: ExtendedActorSystem) extends Lease(settings) {
  import TestLease._

  val log = Logging(system, classOf[TestLease])
  val probe = TestProbe()(system)

  val initialPromise = Promise[Boolean]()

  private val nextAcquireResult = new AtomicReference[Future[Boolean]](initialPromise.future)
  private val nextCheckLeaseResult = new AtomicReference[Boolean](false)
  private val currentCallBack = new AtomicReference[Option[Throwable] => Unit](_ => ())

  log.info("Creating lease {}", settings)

  TestLeaseExt(system).setTestLease(settings.leaseName, this)

  def setNextAcquireResult(next: Future[Boolean]): Unit =
    nextAcquireResult.set(next)

  def setNextCheckLeaseResult(value: Boolean): Unit =
    nextCheckLeaseResult.set(value)

  def getCurrentCallback(): Option[Throwable] => Unit = currentCallBack.get()

  override def acquire(): Future[Boolean] = {
    log.info("acquire, current response " + nextAcquireResult)
    probe.ref ! AcquireReq(settings.ownerName)
    nextAcquireResult.get()
  }

  override def release(): Future[Boolean] = {
    probe.ref ! ReleaseReq(settings.ownerName)
    Future.successful(true)
  }

  override def checkLease(): Boolean = nextCheckLeaseResult.get

  override def acquire(callback: Option[Throwable] => Unit): Future[Boolean] = {
    currentCallBack.set(callback)
    acquire()
  }

}
