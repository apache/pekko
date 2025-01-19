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

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.JavaVersion

import java.lang.invoke.{ MethodHandles, MethodType }
import java.util.concurrent.{ ExecutorService, ForkJoinPool, ForkJoinWorkerThread, ThreadFactory }
import scala.util.control.NonFatal

@InternalApi
private[dispatch] object VirtualThreadSupport {
  private val lookup = MethodHandles.publicLookup()

  /**
   * Is virtual thread supported
   */
  val isSupported: Boolean = JavaVersion.majorVersion >= 21

  /**
   * Create a newThreadPerTaskExecutor with the specified thread factory.
   */
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

  /**
   * Create a virtual thread factory with the default Virtual Thread executor.
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

  /**
   * Create a virtual thread factory with the specified executor as the scheduler of virtual thread.
   */
  def newVirtualThreadFactory(prefix: String, executor: ExecutorService): ThreadFactory =
    try {
      val builderClass = ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder")
      val ofVirtualClass = ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder$OfVirtual")
      val ofVirtualMethod = classOf[Thread].getDeclaredMethod("ofVirtual")
      var builder = ofVirtualMethod.invoke(null)
      if (executor != null) {
        val clazz = builder.getClass
        val field = clazz.getDeclaredField("scheduler")
        field.setAccessible(true)
        field.set(builder, executor)
      }
      val nameMethod = ofVirtualClass.getDeclaredMethod("name", classOf[String], classOf[Long])
      val factoryMethod = builderClass.getDeclaredMethod("factory")
      val zero = java.lang.Long.valueOf(0L)
      builder = nameMethod.invoke(builder, prefix + "-virtual-thread-", zero)
      factoryMethod.invoke(builder).asInstanceOf[ThreadFactory]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to create virtual thread factory", e)
    }

  object CarrierThreadFactory extends ForkJoinPool.ForkJoinWorkerThreadFactory {
    private val clazz = ClassLoader.getSystemClassLoader.loadClass("jdk.internal.misc.CarrierThread")
    // TODO lookup.findClass is only available in Java 9
    private val constructor = clazz.getDeclaredConstructor(classOf[ForkJoinPool])
    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      constructor.newInstance(pool).asInstanceOf[ForkJoinWorkerThread]
    }
  }

  /**
   * Try to get the default scheduler of virtual thread.
   */
  def getVirtualThreadDefaultScheduler: ForkJoinPool =
    try {
      require(isSupported, "Virtual thread is not supported.")
      val clazz = ClassLoader.getSystemClassLoader.loadClass("java.lang.VirtualThread")
      val fieldName = "DEFAULT_SCHEDULER"
      val field = clazz.getDeclaredField(fieldName)
      field.setAccessible(true)
      field.get(null).asInstanceOf[ForkJoinPool]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to get default scheduler of virtual thread.", e)
    }

}
