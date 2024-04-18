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

package org.apache.pekko.persistence.state.exception

import scala.util.control.NoStackTrace

/**
 * Exception thrown when Durable State cannot be updated or deleted.
 *
 * @param msg the exception message
 * @param cause the exception cause
 * @since 1.1.0
 */
abstract class DurableStateException(msg: String, cause: Throwable)
    extends RuntimeException(msg, cause) {
  def this(msg: String) = this(msg, null)
}

/**
 * Exception thrown when Durable State cannot be deleted because the revision
 * is out of date (i.e. the state has a newer revision set).
 *
 * @param msg the exception message
 * @since 1.1.0
 */
final class OutOfDateRevisionException(msg: String)
    extends DurableStateException(msg) with NoStackTrace

/**
 * Exception thrown when Durable State cannot be deleted because the revision
 * is unknown (i.e. the current revision is lower than the revision that we
 * attempted to delete).
 *
 * @param msg the exception message
 * @since 1.1.0
 */
final class UnknownRevisionException(msg: String)
    extends DurableStateException(msg) with NoStackTrace
