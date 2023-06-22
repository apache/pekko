/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed.crdt

object Counter {
  val empty: Counter = Counter(0)

  final case class Updated(delta: BigInt) {

    /**
     * JAVA API
     */
    def this(delta: java.math.BigInteger) = this(delta: BigInt)

    /**
     * JAVA API
     */
    def this(delta: Int) = this(delta: BigInt)
  }
}

final case class Counter(value: BigInt) extends OpCrdt[Counter.Updated] {

  override type T = Counter

  override def applyOperation(event: Counter.Updated): Counter =
    copy(value = value + event.delta)
}
