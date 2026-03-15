/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.stream

/**
 * Marker trait for fan-in graph stages whose output element type is the same as
 * their input element type (i.e., `T => T`) AND whose single-input behavior is
 * semantically equivalent to a no-op pass-through.
 *
 * Examples include [[scaladsl.Merge]], [[scaladsl.Concat]], [[scaladsl.Interleave]],
 * [[scaladsl.MergePrioritized]], and [[scaladsl.OrElse]].
 *
 * Note: [[scaladsl.MergeSequence]] is intentionally '''not''' marked with this trait
 * despite being type-preserving (`T => T`), because it validates sequence ordering
 * even for a single input—its single-input behavior is NOT a no-op pass-through.
 *
 * This trait is used by [[scaladsl.Source.combine]] (and its Java API counterpart)
 * to safely optimize the single-source case. When only one source is provided,
 * the fan-in strategy can be bypassed with a direct pass-through if and only if the
 * strategy is type-preserving AND semantically a no-op for single input. Without this
 * marker, a bypass via `asInstanceOf` would be unsafe for type-transforming strategies
 * like `MergeLatest` (where `T => List[T]`) or `ZipWithN` (where `A => O`).
 *
 * This design uses a "safe default": strategies '''without''' this trait will always
 * be routed through the fan-in graph, even for a single source. This ensures
 * correct behavior for unknown or third-party fan-in strategies that may transform
 * the element type or have semantic side effects beyond type preservation.
 *
 * Note: if a stage with this trait is wrapped (e.g., via `.withAttributes()` or
 * `.named()`), the trait may be lost and the stage will be routed through the
 * fan-in graph instead of being bypassed. This is functionally correct—just
 * slightly less optimal.
 *
 * @since 1.2.0
 */
trait TypePreservingFanIn
