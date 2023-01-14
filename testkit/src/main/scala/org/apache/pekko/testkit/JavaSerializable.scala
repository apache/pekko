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

package org.apache.pekko.testkit

import java.io.Serializable

/**
 * Marker trait for test messages that will use Java serialization via
 * [[org.apache.pekko.testkit.TestJavaSerializer]]
 */
trait JavaSerializable extends Serializable
