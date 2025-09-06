/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util.ccompat

import scala.annotation.Annotation

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Annotation to mark files that need ccompat to be imported for Scala 2.11 and/or 2.12,
 * but not 2.13. Gets rid of the 'unused import' warning on 2.13.
 */
@InternalApi
private[pekko] class ccompatUsedUntil213 extends Annotation
