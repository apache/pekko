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
