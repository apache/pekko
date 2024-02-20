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

package org.apache.pekko.util

import java.io.{ InputStream, ObjectInputStream, ObjectStreamClass }

/**
 * ClassLoaderObjectInputStream tries to utilize the provided ClassLoader to load Classes and falls
 * back to ObjectInputStreams resolver.
 *
 * @param classLoader - the ClassLoader which is to be used primarily
 * @param is - the InputStream that is wrapped
 */
class ClassLoaderObjectInputStream(classLoader: ClassLoader, is: InputStream) extends ObjectInputStream(is) {
  override protected def resolveClass(objectStreamClass: ObjectStreamClass): Class[?] =
    try Class.forName(objectStreamClass.getName, false, classLoader)
    catch {
      case _: ClassNotFoundException => super.resolveClass(objectStreamClass)
    }
}
