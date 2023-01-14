/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.agrona.concurrent.ManyToManyConcurrentArrayQueue

/**
 * INTERNAL API
 */
private[remote] class ObjectPool[A <: AnyRef](capacity: Int, create: () => A, clear: A => Unit) {
  private val pool = new ManyToManyConcurrentArrayQueue[A](capacity)

  def acquire(): A = {
    val obj = pool.poll()
    if (obj eq null) create()
    else obj
  }

  def release(obj: A): Boolean = {
    clear(obj)
    !pool.offer(obj)
  }
}
