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

package org.apache.pekko.stream.scaladsl

import java.io.BufferedReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

import scala.annotation.{ nowarn, tailrec }
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ Await, Promise }
import scala.concurrent.duration.DurationInt
import scala.util.Success
import scala.util.control.NoStackTrace

import com.google.common.jimfs.{ Configuration, Jimfs }

import org.apache.pekko
import pekko.Done
import pekko.stream.{ AbruptTerminationException, ActorAttributes, ActorMaterializer, Attributes, SystemMaterializer }
import pekko.stream.ActorAttributes.supervisionStrategy
import pekko.stream.Supervision.{ restartingDecider, resumingDecider, stoppingDecider }
import pekko.stream.impl.{ PhasedFusingActorMaterializer, StreamSupervisor }
import pekko.stream.impl.StreamSupervisor.Children
import pekko.stream.testkit.{ StreamSpec, TestSubscriber }
import pekko.stream.testkit.Utils.{ assertDispatcher, TE, UnboundedMailboxConfig }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit.EventFilter
import pekko.util.ByteString

class FlowMapWithResourceSpec extends StreamSpec(UnboundedMailboxConfig) {

  private val fs = Jimfs.newFileSystem("MapWithResourceSpec", Configuration.unix())
  private val ex = new Exception("TEST") with NoStackTrace

  private val manyLines = {
    ("a" * 100 + "\n") * 10 +
    ("b" * 100 + "\n") * 10 +
    ("c" * 100 + "\n") * 10 +
    ("d" * 100 + "\n") * 10 +
    ("e" * 100 + "\n") * 10 +
    ("f" * 100 + "\n") * 10
  }
  private val manyLinesArray = manyLines.split("\n")

  private val manyLinesPath = {
    val file = Files.createFile(fs.getPath("/testMapWithResource.dat"))
    Files.write(file, manyLines.getBytes(StandardCharsets.UTF_8))
  }
  private def newBufferedReader() = Files.newBufferedReader(manyLinesPath, StandardCharsets.UTF_8)

  private def readLines(reader: BufferedReader, maxCount: Int): List[String] = {
    if (maxCount == 0) {
      return List.empty
    }

    @tailrec
    def loop(builder: ListBuffer[String], n: Int): ListBuffer[String] = {
      if (n == 0) {
        builder
      } else {
        val line = reader.readLine()
        if (line eq null)
          builder
        else {
          builder += line
          loop(builder, n - 1)
        }
      }
    }
    loop(ListBuffer.empty, maxCount).result()
  }

  "MapWithResource" must {
    "can read contents from a file" in {
      val p = Source(List(1, 10, 20, 30))
        .mapWithResource(() => newBufferedReader())((reader, count) => {
            readLines(reader, count)
          },
          reader => {
            reader.close()
            None
          })
        .mapConcat(identity)
        .runWith(Sink.asPublisher(false))

      val c = TestSubscriber.manualProbe[String]()
      p.subscribe(c)
      val sub = c.expectSubscription()

      val chunks = manyLinesArray.toList.iterator

      sub.request(1)
      c.expectNext() should ===(chunks.next())
      sub.request(1)
      c.expectNext() should ===(chunks.next())
      c.expectNoMessage(300.millis)

      while (chunks.hasNext) {
        sub.request(1)
        c.expectNext() should ===(chunks.next())
      }
      sub.request(1)

      c.expectComplete()
    }

    "continue when Strategy is Resume and exception happened" in {
      val p = Source
        .repeat(1)
        .take(100)
        .mapWithResource(() => newBufferedReader())((reader, _) => {
            val s = reader.readLine()
            if (s != null && s.contains("b")) throw TE("") else s
          },
          reader => {
            reader.close()
            None
          })
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[String]()

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 49).foreach(i => {
        sub.request(1)
        c.expectNext() should ===(if (i < 10) manyLinesArray(i) else manyLinesArray(i + 10))
      })
      sub.request(1)
      c.expectComplete()
    }

    "close and open stream again when Strategy is Restart" in {
      val p = Source
        .repeat(1)
        .take(100)
        .mapWithResource(() => newBufferedReader())((reader, _) => {
            val s = reader.readLine()
            if (s != null && s.contains("b")) throw TE("") else s
          },
          reader => {
            reader.close()
            None
          })
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[String]()

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 19).foreach(_ => {
        sub.request(1)
        c.expectNext() should ===(manyLinesArray(0))
      })
      sub.cancel()
    }

    "work with ByteString as well" in {
      val chunkSize = 50
      val buffer = new Array[Char](chunkSize)
      val p = Source
        .repeat(1)
        .mapWithResource(() => newBufferedReader())((reader, _) => {
            val s = reader.read(buffer)
            if (s > 0) Some(ByteString(buffer.mkString("")).take(s)) else None
          },
          reader => {
            reader.close()
            None
          })
        .takeWhile(_.isDefined)
        .collect {
          case Some(bytes) => bytes
        }
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[ByteString]()

      var remaining = manyLines
      def nextChunk() = {
        val (chunk, rest) = remaining.splitAt(chunkSize)
        remaining = rest
        chunk
      }

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 121).foreach(_ => {
        sub.request(1)
        c.expectNext().utf8String should ===(nextChunk())
      })
      sub.request(1)
      c.expectComplete()
    }

    "use dedicated blocking-io-dispatcher by default" in {
      val p = Source
        .single(1)
        .mapWithResource(() => newBufferedReader())((reader, _) => Option(reader.readLine()),
          reader => {
            reader.close()
            None
          })
        .runWith(TestSink.probe)

      SystemMaterializer(system).materializer
        .asInstanceOf[PhasedFusingActorMaterializer]
        .supervisor
        .tell(StreamSupervisor.GetChildren, testActor)
      val ref = expectMsgType[Children].children.find(_.path.toString contains "mapWithResource").get
      try assertDispatcher(ref, ActorAttributes.IODispatcher.dispatcher)
      finally p.cancel()
    }

    "allow overriding the default dispatcher" in {
      val p = Source
        .single(1)
        .mapWithResource(() => newBufferedReader())(
          (reader, _) => Option(reader.readLine()),
          reader => {
            reader.close()
            None
          })
        .withAttributes(
          Attributes.name("mapWithResourceCustomDispatcher") and
          ActorAttributes.dispatcher("pekko.actor.default-dispatcher")
        )
        .runWith(TestSink.probe)

      SystemMaterializer(system).materializer
        .asInstanceOf[PhasedFusingActorMaterializer]
        .supervisor
        .tell(StreamSupervisor.GetChildren, testActor)
      val ref = expectMsgType[Children].children
        .find(_.path.toString contains "mapWithResourceCustomDispatcher").get
      try assertDispatcher(ref, "pekko.actor.default-dispatcher")
      finally p.cancel()
    }

    "fail when create throws exception" in {
      EventFilter[TE](occurrences = 1).intercept {
        val p = Source
          .single(1)
          .mapWithResource[BufferedReader, String](() => throw TE(""))((reader, _) => reader.readLine(),
            reader => {
              reader.close()
              None
            })
          .runWith(Sink.asPublisher(false))
        val c = TestSubscriber.manualProbe[String]()
        p.subscribe(c)

        c.expectSubscription()
        c.expectError(TE(""))
      }
    }

    "fail when close throws exception" in {
      val (pub, sub) = TestSource[Int]()
        .mapWithResource(() => Iterator("a"))((it, _) => if (it.hasNext) Some(it.next()) else None, _ => throw TE(""))
        .collect { case Some(line) => line }
        .toMat(TestSink())(Keep.both)
        .run()

      pub.ensureSubscription()
      sub.ensureSubscription()
      sub.request(1)
      pub.sendNext(1)
      sub.expectNext("a")
      pub.sendComplete()
      sub.expectError(TE(""))
    }

    "not close the resource twice when read fails" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .mapWithResource(() => 23)( // the best resource there is
          (_, _) => throw TE("failing read"),
          _ => {
            closedCounter.incrementAndGet()
            None
          })
        .runWith(TestSink.probe[Int])

      probe.request(1)
      probe.expectError(TE("failing read"))
      closedCounter.get() should ===(1)
    }

    "not close the resource twice when read fails and then close fails" in {
      val closedCounter = new AtomicInteger(0)
      val probe = Source
        .repeat(1)
        .mapWithResource(() => 23)((_, _) => throw TE("failing read"),
          _ => {
            closedCounter.incrementAndGet()
            if (closedCounter.get == 1) throw TE("boom")
            None
          })
        .runWith(TestSink.probe[Int])

      EventFilter[TE](occurrences = 1).intercept {
        probe.request(1)
        probe.expectError(TE("boom"))
      }
      closedCounter.get() should ===(1)
    }

    "will close the resource when upstream complete" in {
      val closedCounter = new AtomicInteger(0)
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(() => newBufferedReader())((reader, count) => readLines(reader, count),
          reader => {
            reader.close()
            closedCounter.incrementAndGet()
            Some(List("End"))
          })
        .mapConcat(identity)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.expectSubscription().request(2)
      pub.sendNext(1)
      sub.expectNext(manyLinesArray(0))
      pub.sendComplete()
      sub.expectNext("End")
      sub.expectComplete()
      closedCounter.get shouldBe 1
    }

    "will close the resource when upstream fail" in {
      val closedCounter = new AtomicInteger(0)
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(() => newBufferedReader())((reader, count) => readLines(reader, count),
          reader => {
            reader.close()
            closedCounter.incrementAndGet()
            Some(List("End"))
          })
        .mapConcat(identity)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.expectSubscription().request(2)
      pub.sendNext(1)
      sub.expectNext(manyLinesArray(0))
      pub.sendError(ex)
      sub.expectNext("End")
      sub.expectError(ex)
      closedCounter.get shouldBe 1
    }

    "will close the resource when downstream cancel" in {
      val closedCounter = new AtomicInteger(0)
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(() => newBufferedReader())((reader, count) => readLines(reader, count),
          reader => {
            reader.close()
            closedCounter.incrementAndGet()
            Some(List("End"))
          })
        .mapConcat(identity)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      val subscription = sub.expectSubscription()
      subscription.request(2)
      pub.sendNext(1)
      sub.expectNext(manyLinesArray(0))
      subscription.cancel()
      pub.expectCancellation()
      closedCounter.get shouldBe 1
    }

    "will close the resource when downstream fail" in {
      val closedCounter = new AtomicInteger(0)
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(() => newBufferedReader())((reader, count) => readLines(reader, count),
          reader => {
            reader.close()
            closedCounter.incrementAndGet()
            Some(List("End"))
          })
        .mapConcat(identity)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.request(2)
      pub.sendNext(2)
      sub.expectNext(manyLinesArray(0))
      sub.expectNext(manyLinesArray(1))
      sub.cancel(ex)
      pub.expectCancellationWithCause(ex)
      closedCounter.get shouldBe 1
    }

    "will close the resource on abrupt materializer termination" in {
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()
      val promise = Promise[Done]()
      val matVal = Source
        .single(1)
        .mapWithResource(() => {
          newBufferedReader()
        })((reader, count) => readLines(reader, count),
          reader => {
            reader.close()
            promise.complete(Success(Done))
            Some(List("End"))
          })
        .mapConcat(identity)
        .runWith(Sink.never)(mat)
      mat.shutdown()
      matVal.failed.futureValue shouldBe an[AbruptTerminationException]
      Await.result(promise.future, 3.seconds) shouldBe Done
    }

    "will close the autocloseable resource when upstream complete" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(create, (_: AutoCloseable, count) => count)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.expectSubscription().request(2)
      closedCounter.get shouldBe 0
      pub.sendNext(1)
      sub.expectNext(1)
      closedCounter.get shouldBe 0
      pub.sendComplete()
      sub.expectComplete()
      closedCounter.get shouldBe 1
    }

    "will close the autocloseable resource when upstream fail" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(create, (_: AutoCloseable, count) => count)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.expectSubscription().request(2)
      closedCounter.get shouldBe 0
      pub.sendNext(1)
      sub.expectNext(1)
      closedCounter.get shouldBe 0
      pub.sendError(ex)
      sub.expectError(ex)
      closedCounter.get shouldBe 1
    }

    "will close the autocloseable resource when downstream cancel" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(create, (_: AutoCloseable, count) => count)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      val subscription = sub.expectSubscription()
      subscription.request(2)
      closedCounter.get shouldBe 0
      pub.sendNext(1)
      sub.expectNext(1)
      closedCounter.get shouldBe 0
      subscription.cancel()
      pub.expectCancellation()
      closedCounter.get shouldBe 1
    }

    "will close the autocloseable resource when downstream fail" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val (pub, sub) = TestSource
        .probe[Int]
        .mapWithResource(create, (_: AutoCloseable, count) => count)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sub.request(2)
      closedCounter.get shouldBe 0
      pub.sendNext(1)
      sub.expectNext(1)
      closedCounter.get shouldBe 0
      sub.cancel(ex)
      pub.expectCancellationWithCause(ex)
      closedCounter.get shouldBe 1
    }

    "will close the autocloseable resource on abrupt materializer termination" in {
      val closedCounter = new AtomicInteger(0)
      @nowarn("msg=deprecated")
      val mat = ActorMaterializer()
      val promise = Promise[Done]()
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = {
            closedCounter.incrementAndGet()
            promise.complete(Success(Done))
          }
        }
      val matVal = Source
        .single(1)
        .mapWithResource(create, (_: AutoCloseable, count) => count)
        .runWith(Sink.never)(mat)
      closedCounter.get shouldBe 0
      mat.shutdown()
      matVal.failed.futureValue shouldBe an[AbruptTerminationException]
      Await.result(promise.future, 3.seconds) shouldBe Done
      closedCounter.get shouldBe 1
    }

    "continue with autoCloseable when Strategy is Resume and exception happened" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val p = Source
        .fromIterator(() => (0 to 50).iterator)
        .mapWithResource(create,
          (_: AutoCloseable, elem) => {
            if (elem == 10) throw TE("") else elem
          })
        .withAttributes(supervisionStrategy(resumingDecider))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[Int]()

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 48).foreach(i => {
        sub.request(1)
        c.expectNext() should ===(if (i < 10) i else i + 1)
      })
      sub.request(1)
      c.expectNext(50)
      c.expectComplete()
      closedCounter.get shouldBe 1
    }

    "close and open stream with autocloseable again when Strategy is Restart" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val p = Source
        .fromIterator(() => (0 to 50).iterator)
        .mapWithResource(create,
          (_: AutoCloseable, elem) => {
            if (elem == 10 || elem == 20) throw TE("") else elem
          })
        .withAttributes(supervisionStrategy(restartingDecider))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[Int]()

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 30).filter(i => i != 10 && i != 20).foreach(i => {
        sub.request(1)
        c.expectNext() shouldBe i
        closedCounter.get should ===(if (i < 10) 0 else if (i < 20) 1 else 2)
      })
      sub.cancel()
    }

    "stop stream with autoCloseable when Strategy is Stop and exception happened" in {
      val closedCounter = new AtomicInteger(0)
      val create = () =>
        new AutoCloseable {
          override def close(): Unit = closedCounter.incrementAndGet()
        }
      val p = Source
        .fromIterator(() => (0 to 50).iterator)
        .mapWithResource(create,
          (_: AutoCloseable, elem) => {
            if (elem == 10) throw TE("") else elem
          })
        .withAttributes(supervisionStrategy(stoppingDecider))
        .runWith(Sink.asPublisher(false))
      val c = TestSubscriber.manualProbe[Int]()

      p.subscribe(c)
      val sub = c.expectSubscription()

      (0 to 9).foreach(i => {
        sub.request(1)
        c.expectNext() shouldBe i
      })
      sub.request(1)
      c.expectError()
      closedCounter.get shouldBe 1
    }

  }
  override def afterTermination(): Unit = {
    fs.close()
  }
}
