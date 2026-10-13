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

package org.apache.pekko.persistence

import java.io.InputStream

package object serialization {

  /**
   * Converts an input stream to a byte array.
   */
  def streamToBytes(inputStream: InputStream): Array[Byte] =
    inputStream.readAllBytes()
}
