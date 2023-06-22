/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.util.control.NoStackTrace

/**
 * This exception signals that the maximum number of substreams declared has been exceeded.
 * A finite limit is imposed so that memory usage is controlled.
 */
final class TooManySubstreamsOpenException
    extends IllegalStateException("Cannot open a new substream as there are too many substreams open")
    with NoStackTrace {}
