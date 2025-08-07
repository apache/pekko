/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import java.nio.file.{ OpenOption, Path }
import java.nio.file.StandardOpenOption._

import scala.concurrent.Future

import org.apache.pekko
import pekko.stream.IOResult
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.io._
import pekko.util.ByteString

/**
 * Factories to create sinks and sources from files
 */
object FileIO {

  /**
   * Creates a Source from a files contents.
   * Emitted elements are `chunkSize` sized [[pekko.util.ByteString]] elements,
   * except the final element, which will be up to `chunkSize` in size.
   *
   * You can configure the default dispatcher for this Source by changing the `pekko.stream.materializer.blocking-io-dispatcher` or
   * set it for a given Source by using [[pekko.stream.ActorAttributes]].
   *
   * It materializes a [[Future]] of [[IOResult]] containing the number of bytes read from the source file upon completion,
   * and a possible exception if IO operation was not completed successfully. Note that bytes having been read by the source does
   * not give any guarantee that the bytes were seen by downstream stages.
   *
   * @param f         the file path to read from
   * @param chunkSize the size of each read operation, defaults to 8192
   */
  def fromPath(f: Path, chunkSize: Int = 8192): Source[ByteString, Future[IOResult]] =
    fromPath(f, chunkSize, startPosition = 0)

  /**
   * Creates a Source from a files contents.
   * Emitted elements are `chunkSize` sized [[pekko.util.ByteString]] elements,
   * except the final element, which will be up to `chunkSize` in size.
   *
   * You can configure the default dispatcher for this Source by changing the `pekko.stream.materializer.blocking-io-dispatcher` or
   * set it for a given Source by using [[pekko.stream.ActorAttributes]].
   *
   * It materializes a [[Future]] of [[IOResult]] containing the number of bytes read from the source file upon completion,
   * and a possible exception if IO operation was not completed successfully. Note that bytes having been read by the source does
   * not give any guarantee that the bytes were seen by downstream stages.
   *
   * @param f         the file path to read from
   * @param chunkSize the size of each read operation, defaults to 8192
   * @param startPosition the start position to read from
   */
  def fromPath(f: Path, chunkSize: Int, startPosition: Long): Source[ByteString, Future[IOResult]] =
    Source.fromGraph(new FileSource(f, chunkSize, startPosition)).withAttributes(DefaultAttributes.fileSource)

  /**
   * Creates a Sink which writes incoming [[pekko.util.ByteString]] elements to the given file path. Overwrites existing files
   * by truncating their contents as default.
   *
   * Materializes a [[Future]] of [[IOResult]] that will be completed with the size of the file (in bytes) at the streams completion,
   * and a possible exception if IO operation was not completed successfully.
   *
   * This source is backed by an Actor which will use the dedicated `pekko.stream.blocking-io-dispatcher`,
   * unless configured otherwise by using [[pekko.stream.ActorAttributes]].
   *
   * Accepts as arguments a set of [[java.nio.file.StandardOpenOption]], which will determine
   * the underlying behavior when writing the file. If [[java.nio.file.StandardOpenOption.SYNC]] is
   * provided, every update to the file's content be written synchronously to the underlying storage
   * device. Otherwise (the default), the write will be written to the storage device asynchronously
   * by the OS, and may not be stored durably on the storage device at the time the stream completes.
   *
   * @param f the file path to write to
   * @param options File open options, see [[java.nio.file.StandardOpenOption]], defaults to Set(WRITE, TRUNCATE_EXISTING, CREATE)
   */
  def toPath(
      f: Path,
      options: Set[OpenOption] = Set(WRITE, TRUNCATE_EXISTING, CREATE)): Sink[ByteString, Future[IOResult]] =
    toPath(f, options, startPosition = 0)

  /**
   * Creates a Sink which writes incoming [[pekko.util.ByteString]] elements to the given file path. Overwrites existing files
   * by truncating their contents as default.
   *
   * Materializes a [[Future]] of [[IOResult]] that will be completed with the size of the file (in bytes) at the streams completion,
   * and a possible exception if IO operation was not completed successfully.
   *
   * This source is backed by an Actor which will use the dedicated `pekko.stream.blocking-io-dispatcher`,
   * unless configured otherwise by using [[pekko.stream.ActorAttributes]].
   *
   * Accepts as arguments a set of [[java.nio.file.StandardOpenOption]], which will determine
   * the underlying behavior when writing the file. If [[java.nio.file.StandardOpenOption.SYNC]] is
   * provided, every update to the file's content be written synchronously to the underlying storage
   * device. Otherwise (the default), the write will be written to the storage device asynchronously
   * by the OS, and may not be stored durably on the storage device at the time the stream completes.
   *
   * @param f the file path to write to
   * @param options File open options, see [[java.nio.file.StandardOpenOption]], defaults to Set(WRITE, CREATE)
   * @param startPosition the start position to write to
   */
  def toPath(f: Path, options: Set[OpenOption], startPosition: Long): Sink[ByteString, Future[IOResult]] =
    Sink.fromGraph(new FileOutputStage(f, startPosition, options))
}
