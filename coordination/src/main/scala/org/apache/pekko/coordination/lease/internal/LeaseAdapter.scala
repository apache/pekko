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

package org.apache.pekko.coordination.lease.internal

import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.Consumer

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.coordination.lease.LeaseSettings
import pekko.coordination.lease.javadsl.{ Lease => JavaLease }
import pekko.coordination.lease.scaladsl.{ Lease => ScalaLease }

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class LeaseAdapter(delegate: ScalaLease)(implicit val ec: ExecutionContext) extends JavaLease {

  override def acquire(): CompletionStage[java.lang.Boolean] = delegate.acquire().map(Boolean.box).toJava

  override def acquire(leaseLostCallback: Consumer[Optional[Throwable]]): CompletionStage[java.lang.Boolean] = {
    delegate.acquire(o => leaseLostCallback.accept(o.asJava)).map(Boolean.box).toJava
  }

  override def release(): CompletionStage[java.lang.Boolean] = delegate.release().map(Boolean.box).toJava
  override def checkLease(): Boolean = delegate.checkLease()
  override def getSettings(): LeaseSettings = delegate.settings
}

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] class LeaseAdapterToScala(val delegate: JavaLease)(implicit val ec: ExecutionContext)
    extends ScalaLease(delegate.getSettings()) {

  override def acquire(): Future[Boolean] =
    delegate.acquire().toScala.map(Boolean.unbox)

  override def acquire(leaseLostCallback: Option[Throwable] => Unit): Future[Boolean] =
    delegate.acquire(o => leaseLostCallback(o.asScala)).toScala.map(Boolean.unbox)

  override def release(): Future[Boolean] =
    delegate.release().toScala.map(Boolean.unbox)

  override def checkLease(): Boolean =
    delegate.checkLease()
}
