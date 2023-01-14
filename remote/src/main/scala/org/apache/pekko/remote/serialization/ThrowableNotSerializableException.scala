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

package org.apache.pekko.remote.serialization

/**
 * Use as replacement for an original exception when it can't be serialized or deserialized.
 * @param originalMessage the message of the original exception
 * @param originalClassName the class name of the original exception
 * @param cause exception that caused deserialization error, optional and will not be serialized
 */
final class ThrowableNotSerializableException(
    val originalMessage: String,
    val originalClassName: String,
    cause: Throwable)
    extends IllegalArgumentException(s"Serialization of [$originalClassName] failed. $originalMessage", cause) {

  def this(originalMessage: String, originalClassName: String) = this(originalMessage, originalClassName, null)
}
