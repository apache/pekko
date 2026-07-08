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

import java.lang.invoke.{ MethodHandles, MethodType, VarHandle }
import java.util.concurrent.{ Executor, ExecutorService, ForkJoinPool, ForkJoinWorkerThread, ThreadFactory }

import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.util.JavaVersion

@InternalApi
private[dispatch] object VirtualThreadSupport {
  private val zero = java.lang.Long.valueOf(0L)
  private val lookup = MethodHandles.publicLookup()
  private val privateLookup = MethodHandles.lookup()
  private val newThreadPerTaskExecutorMethodType =
    MethodType.methodType(classOf[ExecutorService], classOf[ThreadFactory])
  private val threadFactoryMethodType = MethodType.methodType(classOf[ThreadFactory])
  private val carrierThreadConstructorMethodType = MethodType.methodType(Void.TYPE, classOf[ForkJoinPool])

  // Cached method/var handles to avoid repeated lookups on every invocation
  private lazy val newThreadPerTaskExecutorHandle = {
    val executorsClazz = ClassLoader.getSystemClassLoader.loadClass("java.util.concurrent.Executors")
    lookup.findStatic(executorsClazz, "newThreadPerTaskExecutor", newThreadPerTaskExecutorMethodType)
  }

  private lazy val threadBuilderClass = ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder")
  private lazy val threadBuilderOfVirtualClass =
    ClassLoader.getSystemClassLoader.loadClass("java.lang.Thread$Builder$OfVirtual")

  private lazy val ofVirtualHandle =
    lookup.findStatic(classOf[Thread], "ofVirtual", MethodType.methodType(threadBuilderOfVirtualClass))

  private lazy val ofVirtualNameHandle =
    lookup.findVirtual(threadBuilderOfVirtualClass, "name",
      MethodType.methodType(threadBuilderOfVirtualClass, classOf[String]))

  private lazy val ofVirtualNameWithStartHandle =
    lookup.findVirtual(threadBuilderOfVirtualClass, "name",
      MethodType.methodType(threadBuilderOfVirtualClass, classOf[String], java.lang.Long.TYPE))

  private lazy val builderFactoryHandle =
    lookup.findVirtual(threadBuilderClass, "factory", threadFactoryMethodType)

  private lazy val virtualThreadClass =
    ClassLoader.getSystemClassLoader.loadClass("java.lang.VirtualThread")

  private lazy val defaultSchedulerHandle =
    MethodHandles
      .privateLookupIn(virtualThreadClass, privateLookup)
      .findStaticVarHandle(virtualThreadClass, "DEFAULT_SCHEDULER", classOf[ForkJoinPool])

  private val schedulerHandles = new ClassValue[VarHandle] {
    override def computeValue(clazz: Class[?]): VarHandle =
      MethodHandles
        .privateLookupIn(clazz, privateLookup)
        .findVarHandle(clazz, "scheduler", classOf[Executor])
  }

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
      newThreadPerTaskExecutorHandle.invoke(threadFactory).asInstanceOf[ExecutorService]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to create newThreadPerTaskExecutor.", e)
    }
  }

  /**
   * Start a virtual thread using the default virtual thread scheduler.
   */
  def startVirtualThread(prefix: String, runnable: Runnable): Thread = {
    require(runnable != null, "runnable should not be null.")
    val thread = newVirtualThreadFactory(prefix, -1).newThread(runnable)
    thread.start()
    thread
  }

  /**
   * Create a virtual thread factory with the default Virtual Thread executor.
   * @param prefix the prefix of the virtual thread name.
   * @param start the starting number of the virtual thread name, if -1, the number will not be appended.
   */
  def newVirtualThreadFactory(prefix: String, start: Int): ThreadFactory = {
    newVirtualThreadFactory(prefix, start, null)
  }

  /**
   * Create a virtual thread factory with the default Virtual Thread executor.
   * @param prefix the prefix of the virtual thread name.
   * @param start the starting number of the virtual thread name, if -1, the number will not be appended.
   * @param executor the executor to be used as the scheduler of virtual thread. If null, the default scheduler will be used.
   */
  def newVirtualThreadFactory(prefix: String, start: Int, executor: ExecutorService): ThreadFactory = {
    require(isSupported, "Virtual thread is not supported.")
    require(prefix != null && prefix.nonEmpty, "prefix should not be null or empty.")
    try {
      var builder = ofVirtualHandle.invoke()
      // set the name
      if (start <= -1) {
        builder = ofVirtualNameHandle.invoke(builder, prefix + "-virtual-thread")
      } else {
        builder = ofVirtualNameWithStartHandle.invoke(builder, prefix + "-virtual-thread-", zero)
      }
      // set the scheduler
      if (executor ne null) {
        schedulerHandles.get(builder.getClass).set(builder, executor)
      }
      builderFactoryHandle.invoke(builder).asInstanceOf[ThreadFactory]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to create virtual thread factory", e)
    }
  }

  object CarrierThreadFactory extends ForkJoinPool.ForkJoinWorkerThreadFactory {
    // --add-opens java.base/java.lang=ALL-UNNAMED
    // --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
    private val clazz = ClassLoader.getSystemClassLoader.loadClass("jdk.internal.misc.CarrierThread")
    private val constructorHandle =
      MethodHandles
        .privateLookupIn(clazz, privateLookup)
        .findConstructor(clazz, carrierThreadConstructorMethodType)
    override def newThread(pool: ForkJoinPool): ForkJoinWorkerThread = {
      constructorHandle.invoke(pool).asInstanceOf[ForkJoinWorkerThread]
    }
  }

  /**
   * Try to get the default scheduler of virtual thread.
   */
  def getVirtualThreadDefaultScheduler: ForkJoinPool =
    try {
      require(isSupported, "Virtual thread is not supported.")
      defaultSchedulerHandle.get().asInstanceOf[ForkJoinPool]
    } catch {
      case NonFatal(e) =>
        // --add-opens java.base/java.lang=ALL-UNNAMED
        throw new UnsupportedOperationException("Failed to get default scheduler of virtual thread.", e)
    }

}
