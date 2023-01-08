/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2021-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl

import org.apache.pekko.annotation.InternalStableApi

/**
 * INTERNAL API
 */
@InternalStableApi trait ContextPropagation {
  def suspendContext(): Unit
  def resumeContext(): Unit
  def currentContext(): AnyRef
  def resumeContext(context: AnyRef): Unit
}

/**
 * INTERNAL API
 */
@InternalStableApi object ContextPropagation {

  /**
   * INTERNAL API
   */
  @InternalStableApi def apply(): ContextPropagation = new ContextPropagationImpl
}

private[pekko] final class ContextPropagationImpl extends ContextPropagation {
  def suspendContext(): Unit = ()
  def resumeContext(): Unit = ()
  def currentContext(): AnyRef = null
  def resumeContext(context: AnyRef): Unit = ()
}
