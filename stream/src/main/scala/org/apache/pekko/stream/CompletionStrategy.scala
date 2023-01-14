/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import org.apache.pekko.annotation.{ DoNotInherit, InternalApi }

@DoNotInherit
sealed trait CompletionStrategy

case object CompletionStrategy {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] case object Immediately extends CompletionStrategy

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] case object Draining extends CompletionStrategy

  /**
   * The completion will be signaled immediately even if elements are still buffered.
   */
  def immediately: CompletionStrategy = Immediately

  /**
   * Already buffered elements will be signaled before signaling completion.
   */
  def draining: CompletionStrategy = Draining
}
