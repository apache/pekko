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

package org.apache.pekko.persistence.query.scaladsl

import scala.concurrent.Future

/**
 * A trait that enables querying the current last known sequence number for a given `persistenceId`.
 * @since 1.2.0
 */
trait CurrentLastKnownSequenceNumberByPersistenceIdQuery extends ReadJournal {

  /**
   * Returns the last known sequence number for the given `persistenceId`. Empty if the `persistenceId` is unknown.
   *
   * @param persistenceId The `persistenceId` for which the last known sequence number should be returned.
   * @return Some sequence number or None if the `persistenceId` is unknown.
   */
  def currentLastKnownSequenceNumberByPersistenceId(persistenceId: String): Future[Option[Long]]
}
