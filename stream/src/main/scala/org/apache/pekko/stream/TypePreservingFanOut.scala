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

/**
 * Marker trait for fan-out graph stages whose output element type is the same as
 * their input element type (i.e., `T => T`).
 *
 * Examples include [[scaladsl.Broadcast]] and [[scaladsl.Balance]].
 *
 * Note: [[scaladsl.Partition]] is intentionally NOT marked with this trait despite having
 * `T => T` types, because its `partitioner` function provides user-specified routing
 * semantics that would be lost if the stage were bypassed.
 *
 * This trait is used by [[scaladsl.Sink.combine]] (and its Java API counterpart)
 * to safely optimize the single-sink case. When only one sink is provided,
 * the fan-out strategy can be bypassed with a direct pass-through if and only if the
 * strategy is type-preserving (output type equals input type). Without this marker,
 * a bypass via `asInstanceOf` would be unsafe for type-transforming strategies
 * where `T` differs from `U`.
 *
 * This design uses a "safe default": strategies '''without''' this trait will always
 * be routed through the fan-out graph, even for a single sink. This ensures
 * correct behavior for unknown or third-party fan-out strategies that may transform
 * the element type.
 *
 * @since 1.2.0
 */
trait TypePreservingFanOut
