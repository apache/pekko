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

package org.apache.pekko.stream

import scala.concurrent.TimeoutException
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.annotation.DoNotInherit

/**
 * Base class for timeout exceptions specific to Pekko Streams
 *
 * Not for user extension
 */
@DoNotInherit
sealed class StreamTimeoutException(msg: String) extends TimeoutException(msg) with NoStackTrace

final class InitialTimeoutException(msg: String) extends StreamTimeoutException(msg)

final class CompletionTimeoutException(msg: String) extends StreamTimeoutException(msg)

final class StreamIdleTimeoutException(msg: String) extends StreamTimeoutException(msg)

final class BackpressureTimeoutException(msg: String) extends StreamTimeoutException(msg)
