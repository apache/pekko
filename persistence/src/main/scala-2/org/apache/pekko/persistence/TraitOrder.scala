/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[persistence] object TraitOrder {
  val canBeChecked = true

  def checkBefore(clazz: Class[_], one: Class[_], other: Class[_]): Unit = {
    val interfaces = clazz.getInterfaces
    val i = interfaces.indexOf(other)
    val j = interfaces.indexOf(one)
    if (i != -1 && j != -1 && i < j)
      throw new IllegalStateException(
        s"For ${clazz.getName}, use ${one.getName} with ${other.getName}, instead of ${other.getName} with ${one.getName}")
  }
}
