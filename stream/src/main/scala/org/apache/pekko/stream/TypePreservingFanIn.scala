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
 * Marker trait for fan-in graph stages whose output element type is the same as
 * their input element type (i.e., `T => T`).
 *
 * Built-in stages with this trait: [[scaladsl.Merge]], [[scaladsl.Concat]],
 * [[scaladsl.Interleave]], [[scaladsl.MergePrioritized]], [[scaladsl.OrElse]],
 * and [[scaladsl.MergeSequence]].
 *
 * Note: some of these stages (Concat, Interleave, MergeSequence) have factory methods
 * that wrap the stage via `withDetachedInputs`, which loses this trait. In those cases,
 * `Source.combine` routes through the fan-in graph instead of bypassing—functionally
 * correct, just slightly less optimal. The bypass optimization fires for stages whose
 * factory methods return the raw class (e.g., `Merge`, `MergePrioritized`).
 *
 * This trait is used by [[scaladsl.Source.combine]] (and its Java API counterpart)
 * to safely optimize the single-source case. When only one source is provided,
 * the fan-in strategy can be bypassed with a direct pass-through if and only if the
 * strategy is type-preserving (output type equals input type). Without this marker,
 * a bypass via `asInstanceOf` would be unsafe for type-transforming strategies
 * like `MergeLatest` (where `T => List[T]`) or `ZipWithN` (where `A => O`).
 *
 * This design uses a "safe default": strategies '''without''' this trait will always
 * be routed through the fan-in graph, even for a single source. This ensures
 * correct behavior for unknown or third-party fan-in strategies that may transform
 * the element type.
 *
 * @since 1.2.0
 */
trait TypePreservingFanIn
