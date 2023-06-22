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
import scala.reflect.ClassTag
import scala.util.{ Failure, Success }

import org.apache.pekko
import pekko.actor.{ ClassicActorSystemProvider, ExtendedActorSystem }
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object FlightRecorderLoader {
  def load[T: ClassTag](casp: ClassicActorSystemProvider, fqcn: String, fallback: T): T = {
    val system = casp.classicSystem.asInstanceOf[ExtendedActorSystem]
    if (JavaVersion.majorVersion >= 11 && system.settings.config.getBoolean("pekko.java-flight-recorder.enabled")) {
      // Dynamic instantiation to not trigger class load on earlier JDKs
      system.dynamicAccess.createInstanceFor[T](fqcn, Nil) match {
        case Success(jfr) =>
          jfr
        case Failure(ex) =>
          system.log.warning("Failed to load JFR flight recorder, falling back to noop. Exception: {}", ex.toString)
          fallback
      } // fallback if not possible to dynamically load for some reason
    } else
      // JFR not available on Java 8
      fallback
  }
}
