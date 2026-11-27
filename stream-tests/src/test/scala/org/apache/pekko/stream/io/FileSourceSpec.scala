/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.io

import java.nio.channels.{ FileChannel, FileLock, ReadableByteChannel, WritableByteChannel }
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, NoSuchFileException, StandardOpenOption }
import java.nio.{ ByteBuffer, MappedByteBuffer }
import java.util.Random

import scala.annotation.nowarn
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

import com.google.common.jimfs.{ Configuration, Jimfs }

import org.apache.pekko
import pekko.actor.ActorRef
import pekko.stream.IOResult._
import pekko.stream.SystemMaterializer
import pekko.stream._
import pekko.stream.impl.StreamSupervisor.Children
import pekko.stream.impl.io.FileSource
import pekko.stream.impl.{ PhasedFusingActorMaterializer, StreamSupervisor }
import pekko.stream.io.FileSourceSpec.Settings
import pekko.stream.scaladsl.{ FileIO, Keep, Sink, Source }
import pekko.stream.testkit.Utils._
import pekko.stream.testkit._
import pekko.stream.testkit.scaladsl.TestSink
import pekko.util.ByteString

object FileSourceSpec {
  final case class Settings(chunkSize: Int, readAhead: Int)
}

@nowarn
class FileSourceSpec extends StreamSpec(UnboundedMailboxConfig) {

  val fs = Jimfs.newFileSystem("FileSourceSpec", Configuration.unix())

  val TestText = {
    ("a" * 1000) +
    ("b" * 1000) +
    ("c" * 1000) +
    ("d" * 1000) +
    ("e" * 1000) +
    ("f" * 1000)
  }

  val testFile = {
    val f = Files.createTempFile(fs.getPath("/"), "file-source-spec", ".tmp")
    Files.newBufferedWriter(f, UTF_8).append(TestText).close()
    f
  }

  val notExistingFile = {
    // this way we make sure it doesn't accidentally exist
    val f = Files.createTempFile(fs.getPath("/"), "not-existing-file", ".tmp")
    Files.delete(f)
    f
  }

  val directoryInsteadOfFile = Files.createTempDirectory(fs.getPath("/"), "directory-instead-of-file")

  val LinesCount = 2000 + new Random().nextInt(300)

  val manyLines = {
    val f = Files.createTempFile(fs.getPath("/"), s"file-source-spec-lines_$LinesCount", "tmp")
    val w = Files.newBufferedWriter(f, UTF_8)
    (1 to LinesCount).foreach { l =>
      w.append("a" * l).append("\n")
    }
    w.close()
    f
  }

  def fileSourceChildren(): Set[ActorRef] = {
    SystemMaterializer(system).materializer
      .asInstanceOf[PhasedFusingActorMaterializer]
      .supervisor
      .tell(StreamSupervisor.GetChildren, testActor)
    expectMsgType[Children].children.filter(_.path.toString contains "fileSource").toSet
  }

  def expectNewFileSource(before: Set[ActorRef]): ActorRef =
    awaitAssert {
      val newChildren = fileSourceChildren().diff(before)
      withClue(newChildren) {
        newChildren.size shouldBe 1
        newChildren.head
      }
    }

  private final class ShortReadChannel(delegate: FileChannel) extends FileChannel {
    @volatile var reachedEof = false

    override def read(dst: ByteBuffer, position: Long): Int = {
      val limit = dst.limit()
      dst.limit(dst.position() + math.min(dst.remaining(), 2))
      try {
        val read = delegate.read(dst, position)
        if (read == -1) reachedEof = true
        read
      } finally dst.limit(limit)
    }

    override def position(newPosition: Long): FileChannel = {
      delegate.position(newPosition)
      this
    }

    override def implCloseChannel(): Unit = delegate.close()

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

  "FileSource" must {
    "fill each chunk across short positive reads until reaching EOF" in {
      val file = Files.createTempFile(fs.getPath("/"), "short-reads", ".tmp")
      Files.write(file, "_#abcdefghij".getBytes(UTF_8))
      val channel = new ShortReadChannel(FileChannel.open(file, StandardOpenOption.READ))
      try {
        val (result, probe) = Source
          .fromGraph(new FileSource(file, 4, 2, _ => channel))
          .withAttributes(Attributes.inputBuffer(1, 1))
          .toMat(TestSink())(Keep.both)
          .run()

        probe.request(1).expectNext(ByteString("abcd"))
        probe.request(1).expectNext(ByteString("efgh"))
        probe.request(1).expectNext(ByteString("ij"))
        probe.expectComplete()
        result.futureValue shouldBe createSuccessful(12L)
        channel.reachedEof shouldBe true
      } finally channel.close()
    }

    for (cancel <- List(false, true)) {
      val outcome = if (cancel) "downstream cancellation" else "EOF"
      s"close the channel before completing the IOResult on $outcome" in {
        val channel = FileChannel.open(testFile, StandardOpenOption.READ)
        try {
          val (result, probe) = Source
            .fromGraph(new FileSource(testFile, TestText.length + 1, 0, _ => channel))
            .toMat(TestSink())(Keep.both)
            .run()
          val openOnCompletion = result.map(_ => channel.isOpen)(ExecutionContext.parasitic)
          if (cancel) probe.cancel()
          else {
            probe.request(1).expectNext(ByteString(TestText)).expectComplete()
          }
          openOnCompletion.futureValue shouldBe false
          result.futureValue shouldBe createSuccessful(if (cancel) 0L else TestText.length.toLong)
        } finally channel.close()
      }
    }

    "read contents from a file" in {
      val chunkSize = 512

      val p = FileIO
        .fromPath(testFile, chunkSize)
        .addAttributes(Attributes.inputBuffer(1, 2))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[ByteString]()
      p.subscribe(c)
      val sub = c.expectSubscription()

      var remaining = TestText

      def nextChunk() = {
        val (chunk, rest) = remaining.splitAt(chunkSize)
        remaining = rest
        chunk
      }

      sub.request(1)
      c.expectNext().utf8String should ===(nextChunk().toString)
      sub.request(1)
      c.expectNext().utf8String should ===(nextChunk().toString)
      c.expectNoMessage(300.millis)

      sub.request(200)
      var expectedChunk = nextChunk().toString
      while (expectedChunk != "") {
        c.expectNext().utf8String should ===(expectedChunk)
        expectedChunk = nextChunk().toString
      }
      sub.request(1)

      c.expectComplete()
    }

    "complete future even when abrupt termination happened" in {
      val chunkSize = 512
      val mat = ActorMaterializer()
      val (future, p) = FileIO
        .fromPath(testFile, chunkSize)
        .addAttributes(Attributes.inputBuffer(1, 2))
        .toMat(TestSink())(Keep.both)
        .run()(mat)
      p.request(1)
      p.expectNext().utf8String should ===(TestText.splitAt(chunkSize)._1)
      mat.shutdown()
      future.futureValue === createSuccessful(chunkSize)
    }

    "read partial contents from a file" in {
      val chunkSize = 512
      val startPosition = 1000
      val bufferAttributes = Attributes.inputBuffer(1, 2)

      val p = FileIO
        .fromPath(testFile, chunkSize, startPosition)
        .withAttributes(bufferAttributes)
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[ByteString]()
      p.subscribe(c)
      val sub = c.expectSubscription()
      var remaining = TestText.drop(1000)

      def nextChunk() = {
        val (chunk, rest) = remaining.splitAt(chunkSize)
        remaining = rest
        chunk
      }

      sub.request(5000)

      for (_ <- 1 to 10) {
        c.expectNext().utf8String should ===(nextChunk().toString)
      }
      c.expectComplete()
    }

    "be able to read not whole file" in {
      val chunkSize = 512
      val (future, p) = FileIO
        .fromPath(testFile, chunkSize)
        .addAttributes(Attributes.inputBuffer(1, 2))
        .toMat(TestSink())(Keep.both)
        .run()
      p.request(1)
      p.expectNext().utf8String should ===(TestText.splitAt(chunkSize)._1)
      p.cancel()
      future.futureValue === createSuccessful(chunkSize)
    }

    "complete only when all contents of a file have been signalled" in {
      val chunkSize = 256

      val demandAllButOneChunks = TestText.length / chunkSize - 1

      val p = FileIO
        .fromPath(testFile, chunkSize)
        .addAttributes(Attributes.inputBuffer(4, 8))
        .runWith(Sink.asPublisher(false))

      val c = TestSubscriber.manualProbe[ByteString]()
      p.subscribe(c)
      val sub = c.expectSubscription()

      var remaining = TestText
      def nextChunk() = {
        val (chunk, rest) = remaining.splitAt(chunkSize)
        remaining = rest
        chunk
      }

      sub.request(demandAllButOneChunks)
      for (_ <- 1 to demandAllButOneChunks) c.expectNext().utf8String should ===(nextChunk())
      c.expectNoMessage(300.millis)

      sub.request(1)
      c.expectNext().utf8String should ===(nextChunk())
      c.expectNoMessage(200.millis)

      sub.request(1)
      c.expectNext().utf8String should ===(nextChunk())
      c.expectComplete()
    }

    "onError with failure and return a failed IOResult when trying to read from file which does not exist" in {
      val (r, p) = FileIO.fromPath(notExistingFile).toMat(Sink.asPublisher(false))(Keep.both).run()
      val c = TestSubscriber.manualProbe[ByteString]()
      p.subscribe(c)

      c.expectSubscription()

      val error = c.expectError()
      error shouldBe a[NoSuchFileException]

      r.futureValue.status.isFailure shouldBe true
    }

    "onError with failure and return a failed IOResult when trying to read from a directory instead of a file" in {
      val (r, p) = FileIO.fromPath(directoryInsteadOfFile).toMat(Sink.asPublisher(false))(Keep.both).run()
      val c = TestSubscriber.manualProbe[ByteString]()
      p.subscribe(c)

      c.expectSubscription()

      val error = c.expectError()
      error shouldBe an[IllegalArgumentException]

      r.futureValue.status.isFailure shouldBe true
    }

    List(
      Settings(chunkSize = 512, readAhead = 2),
      Settings(chunkSize = 512, readAhead = 4),
      Settings(chunkSize = 2048, readAhead = 2),
      Settings(chunkSize = 2048, readAhead = 4)).foreach { settings =>
      import settings._

      s"count lines in real file (chunkSize = $chunkSize, readAhead = $readAhead)" in {
        val s =
          FileIO.fromPath(manyLines, chunkSize = chunkSize).withAttributes(Attributes.inputBuffer(readAhead, readAhead))

        val f = s.runWith(Sink.fold(0) { case (acc, l) => acc + l.utf8String.count(_ == '\n') })

        f.futureValue should ===(LinesCount)
      }
    }

    "complete materialized future with a failure if upstream fails" in {
      val matVal: Future[IOResult] =
        FileIO.fromPath(manyLines, chunkSize = 4).map(_ => throw new RuntimeException).to(Sink.ignore).run()

      matVal.failed.futureValue shouldBe a[IOOperationIncompleteException]
    }

    "use dedicated blocking-io-dispatcher by default" in {
      val before = fileSourceChildren()
      val p = FileIO.fromPath(manyLines).runWith(TestSink())
      val ref = expectNewFileSource(before)

      try assertDispatcher(ref, ActorAttributes.IODispatcher.dispatcher)
      finally {
        watch(ref)
        p.cancel()
        expectTerminated(ref)
      }
    }

    "allow overriding the dispatcher using Attributes" in {
      val before = fileSourceChildren()
      val p = FileIO
        .fromPath(manyLines)
        .addAttributes(ActorAttributes.dispatcher("pekko.actor.default-dispatcher"))
        .runWith(TestSink())
      val ref = expectNewFileSource(before)

      try assertDispatcher(ref, "pekko.actor.default-dispatcher")
      finally {
        watch(ref)
        p.cancel()
        expectTerminated(ref)
      }
    }

    "not signal onComplete more than once" in {
      FileIO
        .fromPath(testFile, 2 * TestText.length)
        .runWith(TestSink())
        .requestNext(ByteString(TestText, UTF_8.name))
        .expectComplete()
        .expectNoMessage(1.second)
    }
  }

  override def afterTermination(): Unit = {
    fs.close()
  }

}
