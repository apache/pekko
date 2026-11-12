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

import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.{ SSLContext, SSLEngine, SSLSession }

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Success, Try }

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.TLSProtocol._
import pekko.stream._
import pekko.stream.impl.io.TlsGraphStage
import pekko.stream.scaladsl._
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.TestDuration
import pekko.util.ByteString

import com.typesafe.config.ConfigFactory

/**
 * Edge cases for the [[TlsGraphStage]] path that are awkward to express in the
 * shared [[TlsGraphStageSpec]] matrix: fragmented TLS records, user-side
 * backpressure, materialization isolation, and empty-source shutdown.
 */
class TlsGraphStageEdgeCasesSpec extends AbstractTlsGraphStageEdgeCasesSpec("TLSv1.2")
class TlsGraphStageTls13EdgeCasesSpec extends AbstractTlsGraphStageEdgeCasesSpec("TLSv1.3")

abstract class AbstractTlsGraphStageEdgeCasesSpec(protocol: String)
    extends StreamSpec(TlsGraphStageEdgeCasesSpec.config) {

  private val sslContext: SSLContext = TlsSpec.initSslContext(protocol)
  private val ciphers = (if (protocol == "TLSv1.2") TlsSpec.TLS12Ciphers else TlsSpec.TLS13Ciphers).toArray

  private def engine(role: TLSRole): SSLEngine = {
    val e = sslContext.createSSLEngine()
    e.setUseClientMode(role == Client)
    e.setEnabledCipherSuites(ciphers)
    e.setEnabledProtocols(Array(protocol))
    e
  }

  private def graphStageBidi(
      role: TLSRole,
      closing: TLSClosing = IgnoreComplete,
      verifySession: SSLSession => Try[Unit] = _ => Success(()))
      : BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] =
    BidiFlow
      .fromGraph(new TlsGraphStage(() => engine(role), verifySession, closing))
      .withAttributes(TlsGraphStage.StreamTlsAttributes)

  /** Drive a roundtrip and stop once the expected number of plain bytes arrives. */
  private def collectExactly(
      stream: Source[SslTlsInbound, NotUsed],
      expectedBytes: Int,
      timeout: FiniteDuration = 30.seconds): ByteString = {
    val ((killSwitch, streamDone), result) = stream
      .viaMat(KillSwitches.single)(Keep.right)
      .watchTermination(Keep.both)
      .toMat(
        Flow[SslTlsInbound]
          .collect { case SessionBytes(_, b) => b }
          .scan(ByteString.empty)(_ ++ _)
          .dropWhile(_.size < expectedBytes)
          .toMat(Sink.headOption)(Keep.right))(Keep.both)
      .run()

    try Await.result(result, timeout.dilated).getOrElse(ByteString.empty)
    finally {
      killSwitch.shutdown()
      Await.result(streamDone, timeout.dilated)
    }
  }

  s"TlsGraphStage with $protocol" must {

    "expose async boundary and input buffer attributes for the production path" in {
      val attributes = TlsGraphStage.StreamTlsAttributes

      attributes.contains(Attributes.AsyncBoundary) shouldBe true
      attributes.get[Attributes.InputBuffer] shouldBe Some(
        Attributes.InputBuffer(TlsGraphStage.InputBufferInitialSize, TlsGraphStage.InputBufferMaxSize))
    }

    "name ports according to the plaintext and cipher sides" in {
      val graph = new TlsGraphStage(() => engine(Client), _ => Success(()), IgnoreComplete)

      graph.shape.inlets.map(_.s) shouldBe Seq("TlsGraphStage.plainIn", "TlsGraphStage.cipherIn")
      graph.shape.outlets.map(_.s) shouldBe Seq("TlsGraphStage.cipherOut", "TlsGraphStage.plainOut")
    }

    "be wrapped in its own async island via Attributes.asyncBoundary" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payload = ByteString("ping")
      val received = collectExactly(
        Source.single[SslTlsOutbound](SendBytes(payload))
          .via(client.atop(server.reversed).join(echo)),
        payload.size)
      received shouldBe payload
    }

    "decode cipher payloads delivered as one-byte fragments (BUFFER_UNDERFLOW recovery)" in {
      // Fragmenting cipher chunks to one byte each is the most aggressive way to force
      // BUFFER_UNDERFLOW: every TLS record header (5 bytes) and the entire payload arrive
      // across many separate `onPush` events. The stage must keep pulling more bytes
      // instead of deadlocking once it sees its first underflow.
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val fragmenter = Flow[ByteString].mapConcat(_.toIndexedSeq.map(b => ByteString(b)))
      val transport = BidiFlow.fromFlows(fragmenter, fragmenter)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payload = ByteString("0123456789" * 200) // 2000 bytes of varied content
      val received = collectExactly(
        Source.single[SslTlsOutbound](SendBytes(payload))
          .via(client.atop(transport).atop(server.reversed).join(echo)),
        payload.size,
        timeout = 60.seconds)
      received shouldBe payload
    }

    "batch many small plaintext writes into fewer transport chunks" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val clientToServerChunks = new AtomicInteger
      val serverToClientChunks = new AtomicInteger
      val transport = BidiFlow.fromFlows(
        Flow[ByteString].map { bytes =>
          clientToServerChunks.incrementAndGet()
          bytes
        },
        Flow[ByteString].map { bytes =>
          serverToClientChunks.incrementAndGet()
          bytes
        })
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payloads = List.fill(160)(ByteString("a" * 64))
      val expected = payloads.foldLeft(ByteString.empty)(_ ++ _)

      val received = collectExactly(
        Source(payloads).map[SslTlsOutbound](SendBytes.apply)
          .via(client.atop(transport).atop(server.reversed).join(echo)),
        expected.size,
        timeout = 60.seconds)

      received shouldBe expected
      clientToServerChunks.get should be < (payloads.size / 4)
      serverToClientChunks.get should be < (payloads.size / 4)
    }

    "flush batched small plaintext writes without waiting for upstream completion" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payloads = Vector.fill(64)(ByteString("b" * 64))
      val expected = payloads.foldLeft(ByteString.empty)(_ ++ _)
      val (tail, done) =
        Source(payloads)
          .map[SslTlsOutbound](SendBytes.apply)
          .concatMat(Source.maybe[SslTlsOutbound])(Keep.right)
          .via(client.atop(server.reversed).join(echo))
          .collect { case SessionBytes(_, b) => b }
          .scan(ByteString.empty)(_ ++ _)
          .dropWhile(_.size < expected.size)
          .toMat(Sink.headOption)(Keep.both)
          .run()

      try Await.result(done, 10.seconds.dilated).getOrElse(ByteString.empty) shouldBe expected
      finally tail.trySuccess(None)
    }

    "flush large plaintext writes without waiting for upstream completion" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payload = ByteString(Array.fill[Byte](64 * 1024)('c'.toByte))
      val (tail, done) =
        Source
          .single[SslTlsOutbound](SendBytes(payload))
          .concatMat(Source.maybe[SslTlsOutbound])(Keep.right)
          .via(client.atop(server.reversed).join(echo))
          .collect { case SessionBytes(_, b) => b }
          .scan(ByteString.empty)(_ ++ _)
          .dropWhile(_.size < payload.size)
          .toMat(Sink.headOption)(Keep.both)
          .run()

      try Await.result(done, 10.seconds.dilated).getOrElse(ByteString.empty) shouldBe payload
      finally tail.trySuccess(None)
    }

    "deliver the benchmark burst without losing bytes" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payload = ByteString(Array.fill[Byte](4096)('a'.toByte))
      val payloads = Vector.fill(100)(SendBytes(payload))
      val expectedBytes = payload.size * payloads.size

      val received = collectExactly(
        Source(payloads)
          .via(client.atop(server.reversed).join(echo)),
        expectedBytes,
        timeout = 60.seconds)

      received.size shouldBe expectedBytes
    }

    "deliver the cold-handshake benchmark payload without waiting for stream completion" in {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val payload = ByteString(Array.fill[Byte](4096)('a'.toByte))

      val received = collectExactly(
        Source.single[SslTlsOutbound](SendBytes(payload))
          .via(client.atop(server.reversed).join(echo)),
        payload.size,
        timeout = 10.seconds)

      received shouldBe payload
    }

    "complete repeated cold-handshake benchmark payloads after downstream early cancellation" in {
      val payload = ByteString(Array.fill[Byte](4096)('a'.toByte))

      (1 to 1000).foreach { _ =>
        val client = graphStageBidi(Client)
        val server = graphStageBidi(Server)
        val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
        val received = collectExactly(
          Source.single[SslTlsOutbound](SendBytes(payload))
            .via(client.atop(server.reversed).join(echo)),
          payload.size,
          timeout = 10.seconds)

        received shouldBe payload
      }
    }

    "complete repeated benchmark bursts after downstream early cancellation" in {
      val payload = ByteString(Array.fill[Byte](4096)('a'.toByte))
      val payloads = Vector.fill(100)(SendBytes(payload))
      val expectedBytes = payload.size * payloads.size

      (1 to 50).foreach { _ =>
        val client = graphStageBidi(Client)
        val server = graphStageBidi(Server)
        val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
        val received = collectExactly(
          Source(payloads)
            .via(client.atop(server.reversed).join(echo)),
          expectedBytes,
          timeout = 10.seconds)

        received.size shouldBe expectedBytes
      }
    }

    "deliver all bytes when plainOut downstream requests one chunk at a time" in assertAllStagesStopped {
      val client = graphStageBidi(Client)
      val server = graphStageBidi(Server)
      // Encode both the logical record number and the offset in every eight-byte
      // word. A byte-sized counter would repeat every 256 bytes and could hide
      // a swapped or duplicated 16 KiB record.
      val recordSize = 16384
      val recordCount = 10
      val payload = ByteString.newBuilder
      for (record <- 0 until recordCount; offset <- 0 until recordSize by 8) {
        payload.putInt(record)(ByteOrder.BIG_ENDIAN)
        payload.putInt(offset)(ByteOrder.BIG_ENDIAN)
      }
      val expected = payload.result()
      val records = expected.grouped(recordSize).toVector
      records.distinct.size shouldBe recordCount
      // Check that equal-length mutations cannot satisfy the byte-for-byte oracle.
      (records(1) ++ records.head ++ expected.drop(2 * recordSize)) should not be expected
      (records.head ++ records.head ++ expected.drop(2 * recordSize)) should not be expected
      (expected.slice(256, 512) ++ expected.take(256) ++ expected.drop(512)) should not be expected
      // Writes deliberately cross logical record boundaries; TLS may segment
      // the payload differently and the consumer must not depend on its chunks.
      val blocks = expected.grouped(recordSize + 1).toVector
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val probe = Source(blocks)
        .map[SslTlsOutbound](SendBytes.apply)
        .via(client.atop(server.reversed).join(echo))
        .runWith(TestSink[SslTlsInbound]())

      try within(30.seconds) {
          var received = ByteString.empty
          while (received.size < expected.size) {
            probe.request(1)
            probe.expectNext() match {
              case SessionBytes(session, bytes) =>
                session.getProtocol shouldBe protocol
                bytes should not be empty
                received ++= bytes
              case other => fail(s"Expected plaintext, got $other")
            }
            received.size should be <= expected.size
            // Withhold demand before asking for the next chunk. No sleep or
            // throughput assumption is needed to exercise the stalled output.
            probe.expectNoMessage(10.millis)
          }
          received shouldBe expected
        }
      finally probe.cancel()
    }

    "give each materialization its own SSLEngine (no shared state)" in {
      // Reusing a closed SSLEngine would make the second or third materialization fail.
      def round(payload: ByteString): ByteString = {
        val client = graphStageBidi(Client)
        val server = graphStageBidi(Server)
        val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
        collectExactly(
          Source.single[SslTlsOutbound](SendBytes(payload))
            .via(client.atop(server.reversed).join(echo)),
          payload.size)
      }

      round(ByteString("first")) shouldBe ByteString("first")
      round(ByteString("second")) shouldBe ByteString("second")
      round(ByteString("third")) shouldBe ByteString("third")
    }

    "complete cleanly when source is empty (CompletedImmediately variant)" in {
      // Both sides use EagerClose: an empty source completes immediately, the
      // close cascade fires before any payload travels, and both stages must
      // finalize without errors. No bytes should be delivered.
      val client = graphStageBidi(Client, EagerClose)
      val server = graphStageBidi(Server, EagerClose)
      val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, b) => SendBytes(b) }
      val received = Await.result(
        Source.empty[SslTlsOutbound]
          .via(client.atop(server.reversed).join(echo))
          .collect { case SessionBytes(_, b) => b }
          .runFold(ByteString.empty)(_ ++ _),
        30.seconds.dilated)
      received shouldBe ByteString.empty
    }
  }
}

object TlsGraphStageEdgeCasesSpec {
  val config =
    ConfigFactory
      .parseString("""
        pekko.actor.default-dispatcher.throughput = 1024
        pekko.actor.default-mailbox.mailbox-type = "org.apache.pekko.dispatch.SingleConsumerOnlyUnboundedMailbox"
      """)
      .withFallback(ConfigFactory.parseString(TlsSpec.configOverrides))
}
