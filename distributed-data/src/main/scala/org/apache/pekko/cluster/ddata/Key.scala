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

package org.apache.pekko.cluster.ddata

object Key {

  /**
   * Extract the [[Key#id]].
   */
  def unapply(k: Key[?]): Option[String] = Some(k.id)

  private[pekko] type KeyR = Key[ReplicatedData]

  type KeyId = String

}

/**
 * Key for the key-value data in [[Replicator]]. The type of the data value
 * is defined in the key. Keys are compared equal if the `id` strings are equal,
 * i.e. use unique identifiers.
 *
 * Specific classes are provided for the built in data types, e.g. [[ORSetKey]],
 * and you can create your own keys.
 */
abstract class Key[+T <: ReplicatedData](val id: Key.KeyId) extends Serializable {

  override final def equals(o: Any): Boolean = o match {
    case k: Key[?] => id == k.id
    case _         => false
  }

  override final def hashCode: Int = id.hashCode

  override def toString(): String = id
}
