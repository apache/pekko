/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.internal

import java.util.LinkedList

import scala.concurrent.ExecutionContextExecutor

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final class ControlledExecutor extends ExecutionContextExecutor {
  private val tasks = new LinkedList[Runnable]

  def queueSize: Int = tasks.size()

  def runOne(): Unit = tasks.pop().run()

  def runAll(): Unit = while (!tasks.isEmpty()) runOne()

  def execute(task: Runnable): Unit = {
    tasks.add(task)
  }

  def reportFailure(cause: Throwable): Unit = {
    cause.printStackTrace()
  }
}
