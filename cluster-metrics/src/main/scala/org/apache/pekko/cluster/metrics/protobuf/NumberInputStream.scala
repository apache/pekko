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

import java.io.{ InputStream, ObjectInputStream, ObjectStreamClass }

import org.apache.pekko
import pekko.annotation.InternalApi

/**
 * A special ObjectInputStream that loads will only load built-in primitives or
 * Number classes.
 * <p>
 * This is for internal Pekko use only, and is not intended for public use.
 * </p>
 */
@InternalApi
private[protobuf] class NumberInputStream(inputStream: InputStream) extends ObjectInputStream(inputStream) {

  private val Loader = classOf[NumberInputStream].getClassLoader()

  /**
   * Resolve a class specified by the descriptor using the
   * classloader that loaded NumberInputStream and that treats
   * any class that is not a primitive or a subclass of <code>java.lang.Number</code>
   * as not found.
   *
   * @param objectStreamClass  descriptor of the class
   * @return the Class object described by the ObjectStreamClass
   * @throws ClassNotFoundException if the Class cannot be found (or is rejected)
   */
  override protected def resolveClass(objectStreamClass: ObjectStreamClass): Class[_] = {
    val clazz = Class.forName(objectStreamClass.getName(), false, Loader)
    if (clazz.isPrimitive() || classOf[Number].isAssignableFrom(clazz)) {
      clazz
    } else {
      throw new ClassNotFoundException(
        s"Class rejected: ${objectStreamClass.getName()} (only primitive types and subclasses of java.lang.Number are allowed)")
    }
  }

}
