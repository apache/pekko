/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed.internal

import org.apache.pekko.actor.typed.Props
import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 */
@InternalApi
final private[pekko] case class CachedProps(
    typedProps: Props,
    adaptedProps: org.apache.pekko.actor.Props,
    rethrowTypedFailure: Boolean)
