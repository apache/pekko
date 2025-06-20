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

package org.apache.pekko.cluster.metrics.protobuf

import java.io.{ InputStream, ObjectStreamClass }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.ClassLoaderObjectInputStream

/**
 * A special ObjectInputStream that will only load built-in primitives or
 * Number classes.
 * <p>
 * This is for internal Pekko use only, and is not intended for public use.
 * </p>
 */
@InternalApi
private[protobuf] class NumberInputStream(
    classLoader: ClassLoader,
    inputStream: InputStream) extends ClassLoaderObjectInputStream(classLoader, inputStream) {

  /**
   * Resolve a class specified by the descriptor using the provided classloader
   * and that treats any class that is not a primitive, an array of primitives
   * or a subclass of <code>java.lang.Number</code>
   * or <code>java.math</code> classes as not found.
   *
   * @param objectStreamClass  descriptor of the class
   * @return the Class object described by the ObjectStreamClass
   * @throws ClassNotFoundException if the Class cannot be found (or is rejected)
   */
  override protected def resolveClass(objectStreamClass: ObjectStreamClass): Class[_] = {
    val clazz = super.resolveClass(objectStreamClass)
    if (clazz.isPrimitive() || (clazz.isArray() && clazz.getComponentType.isPrimitive) ||
      classOf[Number].isAssignableFrom(clazz) || clazz.getPackage.getName == "java.math") {
      clazz
    } else {
      throw new ClassNotFoundException(
        s"Class rejected: ${objectStreamClass.getName()} " +
        "(only primitive types, arrays of primitive types, subclasses of java.lang.Number " +
        "and java.math classes are allowed)")
    }
  }

}
