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

package org.apache.pekko.cluster.sharding.internal.jfr

import jdk.jfr.{ Category, Event, Label, StackTrace, Timespan }

import org.apache.pekko.annotation.InternalApi

// requires jdk9+ to compile
// for editing these in IntelliJ, open module settings, change JDK dependency to 11 for only this module

/** INTERNAL API */

@InternalApi
@StackTrace(false)
@Category(Array("Pekko", "Sharding", "Shard")) @Label("Remember Entity Operation")
final class RememberEntityWrite(@Timespan(Timespan.NANOSECONDS) val timeTaken: Long) extends Event

@InternalApi
@StackTrace(false)
@Category(Array("Pekko", "Sharding", "Shard")) @Label("Remember Entity Add")
final class RememberEntityAdd(val entityId: String) extends Event

@InternalApi
@StackTrace(false)
@Category(Array("Pekko", "Sharding", "Shard")) @Label("Remember Entity Remove")
final class RememberEntityRemove(val entityId: String) extends Event

@InternalApi
@StackTrace(false)
@Category(Array("Pekko", "Sharding", "Shard")) @Label("Passivate")
final class Passivate(val entityId: String) extends Event

@InternalApi
@StackTrace(false)
@Category(Array("Pekko", "Sharding", "Shard")) @Label("Passivate Restart")
final class PassivateRestart(val entityId: String) extends Event
