/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.persistence

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Scala 3 specific parent of the [[JournalProtocol.WriteMessages]] companion object, keeping it binary
 * compatible with the companion of the former 3 field case class (`Mirror.Product` parent and an
 * `unapply` returning the instance itself, with `_1`, `_2` and `_3` as the extracted values).
 */
@InternalApi
private[persistence] abstract class WriteMessagesCompanion extends scala.deriving.Mirror.Product with Serializable {
  type MirroredMonoType = JournalProtocol.WriteMessages

  def unapply(w: JournalProtocol.WriteMessages): JournalProtocol.WriteMessages = w

  def fromProduct(p: scala.Product): JournalProtocol.WriteMessages =
    new JournalProtocol.WriteMessages(
      p.productElement(0).asInstanceOf[Seq[PersistentEnvelope]],
      p.productElement(1).asInstanceOf[ActorRef],
      p.productElement(2).asInstanceOf[Int])

  override def toString: String = "WriteMessages"
}
