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
 * Scala 2 specific parent of the [[JournalProtocol.WriteMessages]] companion object, keeping it binary
 * compatible with the companion of the former 3 field case class (`Function3` parent and an `unapply`
 * returning an `Option` of a 3-tuple).
 */
@InternalApi
private[persistence] abstract class WriteMessagesCompanion
    extends scala.runtime.AbstractFunction3[Seq[PersistentEnvelope], ActorRef, Int, JournalProtocol.WriteMessages]
    with Serializable {

  def unapply(w: JournalProtocol.WriteMessages): Option[(Seq[PersistentEnvelope], ActorRef, Int)] =
    Some((w.messages, w.persistentActor, w.actorInstanceId))

  override def toString: String = "WriteMessages"
}
