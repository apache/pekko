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

package org.apache.pekko.dispatch

import org.apache.pekko.annotation.InternalApi
import org.apache.pekko.util.JavaVersion

import java.lang.invoke.{ MethodHandles, MethodType }
import java.util.concurrent.{ ExecutorService, ThreadFactory }
import scala.util.control.NonFatal

@InternalApi
private[dispatch] object VirtualThreadSupport {
  private val lookup = MethodHandles.publicLookup()

  /**
   * Is virtual thread supported
   */
  val isSupported: Boolean = JavaVersion.majorVersion >= 21

  /**
   * Create a virtual thread factory with a executor, the executor will be used as the scheduler of
   * virtual thread.
   */
  def newVirtualThreadFactory(prefix: String): ThreadFactory = {
    require(isSupported, "Virtual thread is not supported.")
    try {
      val builderClass = ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder")
      val ofVirtualClass = ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder$OfVirtual")
      val ofVirtualMethod = lookup.findStatic(classOf[Thread], "ofVirtual", MethodType.methodType(ofVirtualClass))
      var builder = ofVirtualMethod.invoke()
      val nameMethod = lookup.findVirtual(ofVirtualClass, "name",
        MethodType.methodType(ofVirtualClass, classOf[String], classOf[Long]))
      // TODO support replace scheduler when we drop Java 8 support
      val factoryMethod = lookup.findVirtual(builderClass, "factory", MethodType.methodType(classOf[ThreadFactory]))
      builder = nameMethod.invoke(builder, prefix + "-virtual-thread-", 0L)
      factoryMethod.invoke(builder).asInstanceOf[ThreadFactory]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to create virtual thread factory", e)
    }
  }

  def newThreadPerTaskExecutor(threadFactory: ThreadFactory): ExecutorService = {
    require(threadFactory != null, "threadFactory should not be null.")
    try {
      val executorsClazz = ClassLoader.getSystemClassLoader.loadClass("java.util.concurrent.Executors")
      val newThreadPerTaskExecutorMethod = lookup.findStatic(
        executorsClazz,
        "newThreadPerTaskExecutor",
        MethodType.methodType(classOf[ExecutorService], classOf[ThreadFactory]))
      newThreadPerTaskExecutorMethod.invoke(threadFactory).asInstanceOf[ExecutorService]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to create newThreadPerTaskExecutor.", e)
    }
  }

}
