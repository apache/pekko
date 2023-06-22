/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.util.control.NoStackTrace

class StreamTcpException(msg: String) extends RuntimeException(msg) with NoStackTrace

class BindFailedException extends StreamTcpException("bind failed")

@deprecated("BindFailedException object will never be thrown. Match on the class instead.", "2.4.19")
case object BindFailedException extends BindFailedException

class ConnectionException(msg: String) extends StreamTcpException(msg)
