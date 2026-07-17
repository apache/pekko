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

package org.apache.pekko.cluster.sharding

import java.lang.invoke.{ MethodHandles, MethodType }
import java.nio.file.Files
import java.nio.file.Path

import org.apache.pekko
import pekko.actor.{ ActorSystem, ExtendedActorSystem }

/**
 * This will work on JDK11 and JDK8 built with the enable-jfr flag (8u262+).
 *
 * For Pekko JRF recordings you may need to run a publish for multi jvm tests
 * to get the JFR classes compiled.
 */
class FlightRecording(system: ActorSystem) {

  private val lookup = MethodHandles.publicLookup()
  private val noArgVoidMethodType = MethodType.methodType(Void.TYPE)
  private val dumpMethodType = MethodType.methodType(Void.TYPE, classOf[Path])

  private val dynamic = system.asInstanceOf[ExtendedActorSystem].dynamicAccess
  private val recording =
    dynamic.createInstanceFor[AnyRef]("jdk.jfr.Recording", Nil).toOption
  private val clazz = recording.map(_.getClass)
  private val startMethod =
    clazz.map(lookup.findVirtual(_, "start", noArgVoidMethodType))
  private val stopMethod =
    clazz.map(lookup.findVirtual(_, "stop", noArgVoidMethodType))
  private val dumpMethod =
    clazz.map(lookup.findVirtual(_, "dump", dumpMethodType))

  def start() = {
    for {
      r <- recording
      handle <- startMethod
    } yield handle.invoke(r)
  }

  def endAndDump(location: Path) = {
    // Make sure parent directory exists
    if (location.getParent != null)
      Files.createDirectories(location.getParent)

    for {
      r <- recording
      stop <- stopMethod
      dump <- dumpMethod
    } yield {
      stop.invoke(r)
      dump.invoke(r, location)
    }
  }
}
