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

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.{ FileChannel, SeekableByteChannel }
import java.nio.file.StandardOpenOption.{ CREATE, WRITE }
import java.nio.file._

import scala.annotation.nowarn
import scala.collection.mutable.ListBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Success

import com.google.common.jimfs.{ Configuration, Jimfs }

import org.apache.pekko
import pekko.stream.SystemMaterializer
import pekko.stream._
import pekko.stream.impl.StreamSupervisor.Children
import pekko.stream.impl.io.FileOutputStage
import pekko.stream.impl.{ PhasedFusingActorMaterializer, StreamSupervisor }
import pekko.stream.scaladsl.{ FileIO, Keep, Sink, Source }
import pekko.stream.testkit.Utils._
import pekko.stream.testkit._
import pekko.util.ByteString

import org.scalatest.concurrent.ScalaFutures

@nowarn
class FileSinkSpec extends StreamSpec(UnboundedMailboxConfig) with ScalaFutures {

  val fs = Jimfs.newFileSystem("FileSinkSpec", Configuration.unix())

  val TestLines = {
    val b = ListBuffer[String]()
    b.append("a" * 1000 + "\n")
    b.append("b" * 1000 + "\n")
    b.append("c" * 1000 + "\n")
    b.append("d" * 1000 + "\n")
    b.append("e" * 1000 + "\n")
    b.append("f" * 1000 + "\n")
    b.toList
  }

  val TestByteStrings = TestLines.map(ByteString(_))

  private def shortWriteSink(path: Path, startPosition: Long, stopAfterFirstWrite: Boolean = false) =
    Sink.fromGraph(new FileOutputStage(
      () =>
        new SeekableByteChannel {
          private val delegate = FileChannel.open(path, WRITE)
          private var writes = 0

          override def write(src: ByteBuffer): Int = {
            writes += 1
            if (stopAfterFirstWrite && writes > 1) 0
            else {
              val limit = src.limit()
              src.limit(src.position() + math.min(3, src.remaining()))
              try delegate.write(src)
              finally src.limit(limit)
            }
          }

          override def read(dst: ByteBuffer): Int = delegate.read(dst)
          override def position(): Long = delegate.position()
          override def position(newPosition: Long): SeekableByteChannel = {
            delegate.position(newPosition)
            this
          }
          override def size(): Long = delegate.size()
          override def truncate(size: Long): SeekableByteChannel = {
            delegate.truncate(size)
            this
          }
          override def isOpen: Boolean = delegate.isOpen
          override def close(): Unit = delegate.close()
        },
      startPosition))

  "FileSink" must {
    "write lines to a file" in {
      targetFile { f =>
        val completion = Source(TestByteStrings).runWith(FileIO.toPath(f))

        val result = Await.result(completion, 3.seconds)
        result.count should equal(6006)
        checkFileContents(f, TestLines.mkString(""))
      }
    }

    "finish partial writes before moving on to the next chunk" in {
      targetFile { f =>
        Files.write(f, ByteString("::").toArray)
        val completion =
          Source(List(ByteString("hello"), ByteString.empty, ByteString(" world"))).runWith(shortWriteSink(f, 2))

        Await.result(completion, 3.seconds).count shouldBe 11L
        checkFileContents(f, "::hello world")
      }
    }

    "fail without spinning if a nonempty write makes no progress" in {
      targetFile { f =>
        val completion = Source.single(ByteString("hello")).runWith(shortWriteSink(f, 0, stopAfterFirstWrite = true))

        val ex = intercept[IOOperationIncompleteException] { Await.result(completion, 3.seconds) }
        ex.count shouldBe 3L
        ex.getCause shouldBe a[IOException]
        checkFileContents(f, "hel")
      }
    }

    "create new file if not exists" in {
      targetFile({ f =>
          val completion = Source(TestByteStrings).runWith(FileIO.toPath(f))

          val result = Await.result(completion, 3.seconds)
          result.count should equal(6006)
          checkFileContents(f, TestLines.mkString(""))
        }, create = false)
    }

    "write into existing file without wiping existing data" in {
      targetFile { f =>
        def write(lines: List[String]) =
          Source(lines)
            .map(ByteString(_))
            .runWith(FileIO.toPath(f, Set(StandardOpenOption.WRITE, StandardOpenOption.CREATE)))

        val completion1 = write(TestLines)
        Await.result(completion1, 3.seconds)

        val lastWrite = List("x" * 100)
        val completion2 = write(lastWrite)
        val result = Await.result(completion2, 3.seconds)

        result.count should ===(lastWrite.flatten.length.toLong)
        checkFileContents(f, lastWrite.mkString("") + TestLines.mkString("").drop(100))
      }
    }

    "by default replace the existing file" in {
      targetFile { f =>
        def write(lines: List[String]) =
          Source(lines).map(ByteString(_)).runWith(FileIO.toPath(f))

        val completion1 = write(TestLines)
        Await.result(completion1, 3.seconds)

        val lastWrite = List("x" * 100)
        val completion2 = write(lastWrite)
        val result = Await.result(completion2, 3.seconds)

        result.count should ===(lastWrite.flatten.length.toLong)
        checkFileContents(f, lastWrite.mkString(""))
      }
    }

    "allow appending to file" in {
      targetFile { f =>
        def write(lines: List[String] = TestLines) =
          Source(lines).map(ByteString(_)).runWith(FileIO.toPath(f, Set(StandardOpenOption.APPEND)))

        val completion1 = write()
        val result1 = Await.result(completion1, 3.seconds)

        val lastWrite = List("x" * 100)
        val completion2 = write(lastWrite)
        val result2 = Await.result(completion2, 3.seconds)

        Files.size(f) should ===(result1.count + result2.count)
        checkFileContents(f, TestLines.mkString("") + lastWrite.mkString(""))
      }
    }

    "allow writing from specific position to the file" in {
      targetFile { f =>
        val TestLinesCommon = {
          val b = ListBuffer[String]()
          b.append("a" * 1000 + "\n")
          b.append("b" * 1000 + "\n")
          b.append("c" * 1000 + "\n")
          b.append("d" * 1000 + "\n")
          b.toList
        }

        val commonByteString =
          TestLinesCommon.map(ByteString(_)).foldLeft[ByteString](ByteString.empty)((acc, line) => acc ++ line).compact
        val startPosition = commonByteString.size

        val testLinesPart2: List[String] = {
          val b = ListBuffer[String]()
          b.append("x" * 1000 + "\n")
          b.append("x" * 1000 + "\n")
          b.toList
        }

        def write(lines: List[String] = TestLines, startPosition: Long = 0) =
          Source(lines)
            .map(ByteString(_))
            .runWith(FileIO.toPath(f, options = Set(WRITE, CREATE), startPosition = startPosition))

        val completion1 = write()
        Await.result(completion1, 3.seconds)

        val completion2 = write(testLinesPart2, startPosition)
        val result2 = Await.result(completion2, 3.seconds)

        Files.size(f) should ===(startPosition + result2.count)
        checkFileContents(f, TestLinesCommon.mkString("") + testLinesPart2.mkString(""))
      }
    }

    "use dedicated blocking-io-dispatcher by default" in {
      targetFile { f =>
        val forever = Source.maybe.toMat(FileIO.toPath(f))(Keep.left).run()
        try {
          SystemMaterializer(system).materializer
            .asInstanceOf[PhasedFusingActorMaterializer]
            .supervisor
            .tell(StreamSupervisor.GetChildren, testActor)
          val children = expectMsgType[Children]
          val ref = withClue(children) {
            val fileSink = children.children.find(_.path.toString contains "fileSink")
            fileSink shouldBe defined
            fileSink.get
          }
          assertDispatcher(ref, ActorAttributes.IODispatcher.dispatcher)
        } finally {
          forever.complete(Success(None))
        }
      }
    }

    "allow overriding the dispatcher using Attributes" in {
      targetFile { f =>
        val forever = Source.maybe
          .toMat(FileIO.toPath(f).addAttributes(ActorAttributes.dispatcher("pekko.actor.default-dispatcher")))(
            Keep.left)
          .run()
        try {
          SystemMaterializer(system).materializer
            .asInstanceOf[PhasedFusingActorMaterializer]
            .supervisor
            .tell(StreamSupervisor.GetChildren, testActor)
          val ref = expectMsgType[Children].children.find(_.path.toString contains "fileSink").get
          assertDispatcher(ref, "pekko.actor.default-dispatcher")
        } finally {
          forever.complete(Success(None))
        }
      }
    }

    "complete materialized future with an exception when upstream fails" in {
      val te = TE("oh no")
      targetFile { f =>
        val completion = Source(TestByteStrings)
          .map { bytes =>
            if (bytes.contains('b')) throw te
            bytes
          }
          .runWith(FileIO.toPath(f))

        val ex = intercept[IOOperationIncompleteException] { Await.result(completion, 3.seconds) }
        ex.count should equal(1001)
        ex.getCause should equal(te)
        checkFileContents(f, TestLines.takeWhile(!_.contains('b')).mkString(""))
      }
    }

    "complete with failure when file cannot be open" in {
      val completion =
        Source.single(ByteString("42")).runWith(FileIO.toPath(fs.getPath("/I/hope/this/file/doesnt/exist.txt")))

      completion.failed.futureValue.getCause shouldBe an[NoSuchFileException]
    }
  }

  private def targetFile(block: Path => Unit, create: Boolean = true): Unit = {
    val targetFile = Files.createTempFile(fs.getPath("/"), "synchronous-file-sink", ".tmp")
    if (!create) Files.delete(targetFile)
    try block(targetFile)
    finally Files.delete(targetFile)
  }

  def checkFileContents(f: Path, contents: String): Unit = {
    val out = Files.readAllBytes(f)
    new String(out) should ===(contents)
  }

  override def afterTermination(): Unit = {
    fs.close()
  }

}
