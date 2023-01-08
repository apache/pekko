/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.testkit

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream }

import org.apache.pekko
import pekko.actor.ExtendedActorSystem
import pekko.serialization.{ BaseSerializer, JavaSerializer }
import pekko.util.ClassLoaderObjectInputStream

/**
 * This Serializer uses standard Java Serialization and is useful for tests where ad-hoc messages are created and sent
 * between actor systems. It needs to be explicitly enabled in the config (or through `ActorSystemSetup`) like so:
 *
 * ```
 * pekko.actor.serialization-bindings {
 *   "my.test.AdHocMessage" = java-test
 * }
 * ```
 */
class TestJavaSerializer(val system: ExtendedActorSystem) extends BaseSerializer {

  def includeManifest: Boolean = false

  def toBinary(o: AnyRef): Array[Byte] = {
    val bos = new ByteArrayOutputStream
    val out = new ObjectOutputStream(bos)
    JavaSerializer.currentSystem.withValue(system) { out.writeObject(o) }
    out.close()
    bos.toByteArray
  }

  def fromBinary(bytes: Array[Byte], clazz: Option[Class[_]]): AnyRef = {
    val in = new ClassLoaderObjectInputStream(system.dynamicAccess.classLoader, new ByteArrayInputStream(bytes))
    val obj = JavaSerializer.currentSystem.withValue(system) { in.readObject }
    in.close()
    obj
  }
}
