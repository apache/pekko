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

package org.apache.pekko.stream.io

import java.io.IOException
import java.nio.channels.{ FileChannel, FileLock, ReadableByteChannel, WritableByteChannel }
import java.nio.file.{ Files, StandardOpenOption }
import java.nio.{ ByteBuffer, MappedByteBuffer }

import com.google.common.jimfs.{ Configuration, Jimfs }

import org.apache.pekko
import pekko.event.Logging
import pekko.stream.IOResult
import pekko.stream.impl.io.FileSource
import pekko.stream.scaladsl.{ Keep, Source }
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.EventFilter
import pekko.util.ByteString

class FileSourceCloseFailureSpec extends StreamSpec {
  "FileSource" must {
    for (readFails <- List(false, true)) {
      val outcome = if (readFails) "after a read failure" else "after EOF"
      s"log a channel close failure $outcome and preserve IOResult precedence" in {
        val fs = Jimfs.newFileSystem(Configuration.unix())
        try {
          val body = ByteString("hello")
          val file = Files.createTempFile(fs.getPath("/"), "file-source-close", ".tmp")
          Files.write(file, body.toArray)
          val closeFailure = new IOException("test close failure")
          val readFailure = new IOException("test read failure")
          val channel = new CloseFailingChannel(
            FileChannel.open(file, StandardOpenOption.READ), closeFailure, Option.when(readFails)(readFailure))

          EventFilter.custom({
              case error: Logging.Error =>
                (error.cause eq closeFailure) && error.message == "Failed to close FileSource channel"
            }, occurrences = 1).intercept {
            val (result, subscriber) = Source
              .fromGraph(new FileSource(file, body.length + 1, 0L, _ => channel))
              .toMat(TestSink())(Keep.both)
              .run()

            subscriber.request(1)
            if (readFails) (subscriber.expectError() should be).theSameInstanceAs(readFailure)
            else subscriber.expectNext(body).expectComplete()
            val expected = if (readFails) IOResult.createFailed(0L, readFailure)
            else IOResult.createFailed(body.length.toLong, closeFailure)
            result.futureValue shouldBe expected
          }
        } finally fs.close()
      }
    }
  }

  private final class CloseFailingChannel(delegate: FileChannel, closeFailure: IOException,
      readFailure: Option[IOException])
      extends FileChannel {
    override def read(dst: ByteBuffer, position: Long): Int = readFailure match {
      case Some(ex) => throw ex
      case None     => delegate.read(dst, position)
    }

    override def position(newPosition: Long): FileChannel = {
      delegate.position(newPosition)
      this
    }

    override def implCloseChannel(): Unit = {
      delegate.close()
      throw closeFailure
    }

    private def unsupported(): Nothing = throw new UnsupportedOperationException

    override def read(dst: ByteBuffer): Int = unsupported()
    override def read(dsts: Array[ByteBuffer], offset: Int, length: Int): Long = unsupported()
    override def write(src: ByteBuffer): Int = unsupported()
    override def write(srcs: Array[ByteBuffer], offset: Int, length: Int): Long = unsupported()
    override def write(src: ByteBuffer, position: Long): Int = unsupported()
    override def position(): Long = unsupported()
    override def size(): Long = unsupported()
    override def truncate(size: Long): FileChannel = unsupported()
    override def force(metaData: Boolean): Unit = unsupported()
    override def transferTo(position: Long, count: Long, target: WritableByteChannel): Long = unsupported()
    override def transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = unsupported()
    override def map(mode: FileChannel.MapMode, position: Long, size: Long): MappedByteBuffer = unsupported()
    override def lock(position: Long, size: Long, shared: Boolean): FileLock = unsupported()
    override def tryLock(position: Long, size: Long, shared: Boolean): FileLock = unsupported()
  }
}
