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

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import javax.net.ssl.{ SSLContext, SSLEngine, SSLEngineResult, SSLSession }
import javax.net.ssl.SSLEngineResult.HandshakeStatus._

import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.NotUsed
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.{ TlsGraphStage, TlsModule }
import pekko.stream.scaladsl._
import pekko.stream.testkit.{ StreamSpec, TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import pekko.testkit.WithLogCapturing
import pekko.util.ByteString

/**
 * Pins the inbound contract of both TLS engines when the transport (ciphertext) output is cancelled:
 * records already received from the peer either reach the plaintext output or are reported as a
 * truncated session. Silently dropping them is what these tests must keep catching.
 */
class TlsTransportOutputCancelSpec extends StreamSpec(TlsSpec.configOverrides) with WithLogCapturing {

  private val Protocols = List("TLSv1.2", "TLSv1.3")

  private def tlsBidi(
      context: SSLContext,
      role: TLSRole,
      closing: TLSClosing,
      useLegacyActor: Boolean): BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] = {
    def createEngine(): SSLEngine = {
      val engine = context.createSSLEngine()
      engine.setUseClientMode(role == Client)
      engine.setEnabledProtocols(Array(context.getProtocol))
      engine
    }
    val verifySession: SSLSession => scala.util.Try[Unit] = _ => Success(())
    if (useLegacyActor)
      BidiFlow.fromGraph(TlsModule(Attributes.none, createEngine _, verifySession, closing))
    else
      BidiFlow
        .fromGraph(new TlsGraphStage(createEngine _, verifySession, closing))
        .withAttributes(TlsGraphStage.StreamTlsAttributes)
  }

  /** A plain `SSLEngine` driving the ciphertext ports of the operator under test. */
  private final class Peer(
      context: SSLContext,
      cipherIn: TestPublisher.Probe[ByteString],
      cipherOut: TestSubscriber.Probe[ByteString]) {
    private val engine: SSLEngine = context.createSSLEngine()
    engine.setUseClientMode(true)
    engine.setEnabledProtocols(Array(context.getProtocol))

    private var cipherBuffer = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
    private var plainBuffer = ByteBuffer.allocate(engine.getSession.getApplicationBufferSize)
    private var decoded = ByteString.empty
    private var outstandingDemand = 0L

    def handshake(): Unit = {
      engine.beginHandshake()
      var status = engine.getHandshakeStatus
      while (status != NOT_HANDSHAKING && status != FINISHED) {
        status match {
          case NEED_WRAP   => send(ByteString.empty)
          case NEED_UNWRAP => if (!decodeBuffered()) receive()
          case NEED_TASK   => runDelegatedTasks()
          case other       => fail(s"unexpected handshake status $other")
        }
        status = engine.getHandshakeStatus
      }
    }

    /** Ciphertext elements the operator may push before this peer has to ask again. */
    def receiveDemand(elements: Long): Unit = {
      cipherOut.request(elements)
      outstandingDemand += elements
    }

    def cancelCipherOut(): Unit = cipherOut.cancel()

    def expectHandshakeOutput(): Unit = {
      cipherOut.expectNext().nonEmpty shouldBe true
      outstandingDemand -= 1
    }

    def expectCipherInCancellation(): Unit = cipherIn.expectCancellation()

    def receiveCloseNotify(): Unit =
      while (!engine.isInboundDone)
        if (!decodeBuffered()) receive()

    def sendCloseNotify(): Unit = {
      engine.closeOutbound()
      send(ByteString.empty)
    }

    def sendPartialRecord(text: String): ByteString = {
      val ciphertext = encrypt(ByteString(text, StandardCharsets.US_ASCII))
      sendCiphertext(ciphertext.take(3))
      ciphertext.drop(3)
    }

    def sendCiphertext(bytes: ByteString): Unit = cipherIn.sendNext(bytes)

    def send(plaintext: ByteString): Unit = sendCiphertext(encrypt(plaintext))

    private def encrypt(plaintext: ByteString): ByteString = {
      val packet = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
      val source = ByteBuffer.allocate(plaintext.size)
      plaintext.copyToBuffer(source)
      source.flip()
      val result = engine.wrap(source, packet)
      result.getStatus shouldBe
      (if (engine.isOutboundDone) SSLEngineResult.Status.CLOSED else SSLEngineResult.Status.OK)
      packet.flip()
      ByteString(packet)
    }

    def sendText(text: String): Unit = send(ByteString(text, StandardCharsets.US_ASCII))

    /** Reads ciphertext one element at a time until `expected` plaintext bytes arrived. */
    def receiveText(expected: Int): ByteString = {
      while (decoded.size < expected)
        if (!decodeBuffered()) receive()
      val result = decoded
      decoded = ByteString.empty
      result
    }

    private def receive(): Unit = {
      if (outstandingDemand == 0) receiveDemand(1)
      outstandingDemand -= 1
      append(cipherOut.expectNext())
    }

    private def runDelegatedTasks(): Unit = {
      var task = engine.getDelegatedTask
      while (task ne null) {
        task.run()
        task = engine.getDelegatedTask
      }
    }

    private def append(bytes: ByteString): Unit = {
      if (cipherBuffer.remaining < bytes.size) {
        val grown = ByteBuffer.allocate(cipherBuffer.position() + bytes.size)
        cipherBuffer.flip()
        grown.put(cipherBuffer)
        cipherBuffer = grown
      }
      bytes.copyToBuffer(cipherBuffer)
    }

    private def decodeBuffered(): Boolean = {
      cipherBuffer.flip()
      val result = engine.unwrap(cipherBuffer, plainBuffer)
      cipherBuffer.compact()
      result.getStatus match {
        case SSLEngineResult.Status.BUFFER_OVERFLOW =>
          val grown = ByteBuffer.allocate(plainBuffer.capacity * 2)
          plainBuffer.flip()
          grown.put(plainBuffer)
          plainBuffer = grown
          true
        case SSLEngineResult.Status.BUFFER_UNDERFLOW => false
        case _                                       =>
          plainBuffer.flip()
          decoded ++= ByteString(plainBuffer)
          plainBuffer.clear()
          result.bytesConsumed > 0 || result.bytesProduced > 0
      }
    }
  }

  private final class Harness(
      val plainIn: TestPublisher.Probe[SslTlsOutbound],
      val plainOut: TestSubscriber.Probe[SslTlsInbound],
      val peer: Peer)

  private def newHarness(
      protocol: String,
      useLegacyActor: Boolean,
      closing: TLSClosing = IgnoreComplete,
      handshake: Boolean = true): Harness = {
    val context = TlsSpec.initSslContext(protocol)
    val probes = RunnableGraph
      .fromGraph(GraphDSL.createGraph(
        TestSource[SslTlsOutbound](),
        TestSink[SslTlsInbound](),
        TestSource[ByteString](),
        TestSink[ByteString]())(Tuple4.apply) { implicit b => (plainIn, plainOut, cipherIn, cipherOut) =>
        import GraphDSL.Implicits._
        val tls = b.add(tlsBidi(context, Server, closing, useLegacyActor))
        plainIn  ~> tls.in1
        tls.out1 ~> cipherOut
        cipherIn ~> tls.in2
        tls.out2 ~> plainOut
        ClosedShape
      })
      .run()

    val peer = new Peer(context, probes._3, probes._4)
    val harness = new Harness(probes._1, probes._2, peer)

    // Inbound decryption needs plaintext demand, so the handshake consumes one request.
    harness.plainOut.ensureSubscription()
    probes._4.ensureSubscription()
    if (handshake) {
      harness.plainOut.request(1)
      peer.receiveDemand(32)
      peer.handshake()
    }
    harness
  }

  /** Establishes the session and leaves the plaintext output with no outstanding demand. */
  private def establishIdleSession(harness: Harness): SSLSession = {
    harness.plainIn.sendNext(SendBytes(ByteString("ready")))
    harness.peer.receiveText(5) shouldBe ByteString("ready")
    harness.peer.sendText("ping")
    val received = harness.plainOut.expectNext().asInstanceOf[SessionBytes]
    received.bytes shouldBe ByteString("ping")
    received.session
  }

  private def expectSessionBytes(inbound: SslTlsInbound, text: String): Unit = inbound match {
    case SessionBytes(_, bytes) => bytes shouldBe ByteString(text, StandardCharsets.US_ASCII)
    case other                  => fail(s"expected SessionBytes($text) but got $other")
  }

  "A TLS stage whose transport output is cancelled" must {
    for {
      protocol <- Protocols
      useLegacyActor <- List(false, true)
    } {
      val engine = if (useLegacyActor) "legacy actor" else "graph stage"

      s"complete without truncation before the initial handshake ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor, handshake = false)
          harness.peer.cancelCipherOut()
          harness.peer.expectCipherInCancellation()
          harness.plainIn.expectCancellation()
          harness.plainOut.expectComplete()
        }
      }

      for (closing <- List(IgnoreComplete, IgnoreCancel, IgnoreBoth, EagerClose)) {
        s"report cancellation with no buffered input and preserve session validity ($engine, $protocol, $closing)" in {
          assertAllStagesStopped {
            val harness = newHarness(protocol, useLegacyActor, closing)
            val session = establishIdleSession(harness)
            session.isValid shouldBe true
            harness.peer.cancelCipherOut()
            harness.plainOut.request(1)
            harness.plainOut.expectNext() shouldBe SessionTruncated
            harness.plainOut.expectComplete()
            harness.peer.expectCipherInCancellation()
            harness.plainIn.expectCancellation()
            session.isValid shouldBe true
          }
        }

        s"stop when the plaintext output cancels while the truncation report waits ($engine, $protocol, $closing)" in {
          assertAllStagesStopped {
            val harness = newHarness(protocol, useLegacyActor, closing)
            establishIdleSession(harness)
            harness.peer.cancelCipherOut()
            harness.peer.expectCipherInCancellation()
            harness.plainIn.expectCancellation()
            harness.plainOut.cancel()
          }
        }
      }

      if (protocol == "TLSv1.2") {
        s"report cancellation during renegotiation of an established session ($engine)" in {
          assertAllStagesStopped {
            val harness = newHarness(protocol, useLegacyActor)
            establishIdleSession(harness)
            harness.plainIn.sendNext(NegotiateNewSession)
            // Observe the HelloRequest without answering it: the second handshake remains in flight.
            harness.peer.expectHandshakeOutput()
            harness.peer.sendText("first")
            harness.peer.sendText("second")
            harness.plainOut.expectNoMessage(200.millis)
            harness.peer.cancelCipherOut()
            harness.plainOut.request(3)
            expectSessionBytes(harness.plainOut.expectNext(), "first")
            harness.plainOut.expectNext() shouldBe SessionTruncated
            harness.plainOut.expectComplete()
          }
        }
      }

      s"report cancellation after the plaintext input completes ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor)
          establishIdleSession(harness)
          harness.plainIn.sendComplete()
          // An inbound round moves the pump to outboundClosed after observing input completion.
          harness.peer.sendText("last")
          harness.plainOut.request(1)
          expectSessionBytes(harness.plainOut.expectNext(), "last")
          harness.peer.cancelCipherOut()
          harness.peer.expectCipherInCancellation()
          harness.plainOut.request(1)
          harness.plainOut.expectNext() shouldBe SessionTruncated
          harness.plainOut.expectComplete()
        }
      }

      s"report a truncated session when buffered inbound records cannot all be delivered ($engine, $protocol)" in {
        val harness = newHarness(protocol, useLegacyActor)
        within(10.seconds) {
          establishIdleSession(harness)

          // Two records arrive while the plaintext output has no demand, so both stay buffered.
          harness.peer.sendText("first")
          harness.plainOut.expectNoMessage(200.millis)
          harness.peer.sendText("second")
          harness.plainOut.expectNoMessage(200.millis)

          harness.peer.cancelCipherOut()
          harness.plainOut.request(2)

          expectSessionBytes(harness.plainOut.expectNext(), "first")
          // The remaining record is unreachable, so the abandonment must be observable.
          harness.plainOut.expectNext() shouldBe SessionTruncated
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"still deliver a single buffered record before reporting truncation ($engine, $protocol)" in {
        val harness = newHarness(protocol, useLegacyActor)
        within(10.seconds) {
          establishIdleSession(harness)
          harness.peer.sendText("response")
          harness.plainOut.expectNoMessage(200.millis)

          harness.peer.cancelCipherOut()
          // Cancellation alone must not complete the plaintext output.
          harness.plainOut.expectNoMessage(200.millis)

          harness.plainOut.request(2)
          expectSessionBytes(harness.plainOut.expectNext(), "response")
          harness.plainOut.expectNext() shouldBe SessionTruncated
          harness.plainOut.expectComplete()
        }
      }

      s"cancel plaintext input before waiting to deliver buffered input ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor)
          establishIdleSession(harness)
          harness.peer.sendText("response")
          harness.peer.cancelCipherOut()

          harness.plainIn.expectCancellation()
          harness.plainOut.request(2)
          expectSessionBytes(harness.plainOut.expectNext(), "response")
          harness.plainOut.expectNext(SessionTruncated)
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"finish an already initiated close handshake after transport output cancellation ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor, EagerClose)
          establishIdleSession(harness)
          harness.plainIn.sendComplete()
          harness.peer.receiveCloseNotify()
          harness.peer.cancelCipherOut()
          harness.peer.sendCloseNotify()

          harness.plainOut.request(1)
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"stop waiting for the peer close when both outputs are cancelled ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor, EagerClose)
          establishIdleSession(harness)
          harness.plainIn.sendComplete()
          harness.peer.receiveCloseNotify()
          harness.peer.cancelCipherOut()
          harness.plainOut.cancel()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"report cancellation of a partial record without waiting for more ciphertext ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor)
          establishIdleSession(harness)
          harness.peer.sendPartialRecord("response")
          harness.peer.cancelCipherOut()
          harness.plainOut.request(1)

          harness.plainOut.expectNext(SessionTruncated)
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
          harness.plainIn.expectCancellation()
        }
      }

      s"assemble a partial record while the transport output stays open ($engine, $protocol)" in {
        assertAllStagesStopped {
          val harness = newHarness(protocol, useLegacyActor)
          establishIdleSession(harness)
          val remainder = harness.peer.sendPartialRecord("response")
          harness.plainOut.request(1)
          harness.plainOut.expectNoMessage(100.millis)
          harness.peer.sendCiphertext(remainder)
          expectSessionBytes(harness.plainOut.expectNext(), "response")

          harness.peer.cancelCipherOut()
          harness.plainOut.request(1)
          harness.plainOut.expectNext(SessionTruncated)
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"report a truncated session when the buffered records outnumber the plaintext demand ($engine, $protocol)" in {
        val harness = newHarness(protocol, useLegacyActor)
        within(10.seconds) {
          establishIdleSession(harness)
          List("first", "second", "third").foreach { text =>
            harness.peer.sendText(text)
            harness.plainOut.expectNoMessage(200.millis)
          }

          harness.peer.cancelCipherOut()

          // One element at a time: the truncation report must survive demand that is only granted
          // after the last buffered record was already delivered.
          harness.plainOut.request(1)
          expectSessionBytes(harness.plainOut.expectNext(), "first")
          harness.plainOut.request(1)
          harness.plainOut.expectNext() shouldBe SessionTruncated
          harness.plainOut.expectComplete()
          harness.peer.expectCipherInCancellation()
        }
      }

      s"deliver every buffered record while the transport output stays open ($engine, $protocol)" in {
        val harness = newHarness(protocol, useLegacyActor)
        within(10.seconds) {
          establishIdleSession(harness)
          harness.peer.sendText("first")
          harness.plainOut.expectNoMessage(200.millis)
          harness.peer.sendText("second")
          harness.plainOut.expectNoMessage(200.millis)

          harness.plainOut.request(2)
          expectSessionBytes(harness.plainOut.expectNext(), "first")
          expectSessionBytes(harness.plainOut.expectNext(), "second")
          // Nothing was abandoned, so no truncation may be reported and the stage stays open.
          harness.plainOut.expectNoMessage(200.millis)

          harness.peer.cancelCipherOut()
          harness.plainOut.cancel()
          harness.peer.expectCipherInCancellation()
        }
      }
    }
  }

  "The legacy actor TLS stage" must {
    Protocols.foreach { protocol =>
      s"cancel its read side when plaintext arrives after the transport output was cancelled ($protocol)" in {
        val harness = newHarness(protocol, useLegacyActor = true)
        within(10.seconds) {
          establishIdleSession(harness)

          // Cancellation leaves the transport output's demand flag set, so plaintext arriving
          // afterwards still makes the outbound side runnable and reaches the teardown path while
          // the plaintext output has no demand at all.
          harness.peer.cancelCipherOut()
          harness.plainIn.sendNext(SendBytes(ByteString("tail")))

          // Parking the pump to report a truncation must release both upstream subscriptions.
          harness.peer.expectCipherInCancellation()
          harness.plainIn.expectCancellation()

          harness.plainOut.request(1)
          harness.plainOut.expectNext() shouldBe SessionTruncated
          harness.plainOut.expectComplete()
        }
      }
    }
  }
}
