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
import javax.net.ssl.{ SSLContext, SSLEngine, SSLEngineResult }
import javax.net.ssl.SSLEngineResult.HandshakeStatus._

import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.TlsGraphStage
import pekko.stream.scaladsl._
import pekko.stream.stage._
import pekko.stream.testkit.{ StreamSpec, TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit.{ TestProbe, WithLogCapturing }
import pekko.util.ByteString

class TlsGraphStageCancellationSpec extends StreamSpec(TlsSpec.configOverrides) with WithLogCapturing {

  private def tls(context: SSLContext, client: Boolean, closing: TLSClosing = IgnoreComplete) =
    BidiFlow
      .fromGraph(new TlsGraphStage(
        () => {
          val engine = context.createSSLEngine()
          engine.setUseClientMode(client)
          engine.setEnabledProtocols(Array(context.getProtocol))
          engine
        },
        _ => Success(()),
        closing))
      .withAttributes(TlsGraphStage.StreamTlsAttributes)

  private class EchoThenCancel extends GraphStage[FlowShape[ByteString, SslTlsOutbound]] {
    val in = Inlet[ByteString]("EchoThenCancel.in")
    val out = Outlet[SslTlsOutbound]("EchoThenCancel.out")
    override val shape = FlowShape(in, out)

    override def createLogic(attributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var first = true
      private var cancelOnPull = false
      setHandler(in,
        new InHandler {
          override def onPush(): Unit = {
            push(out, SendBytes(grab(in)))
            if (first) first = false
            else cancelOnPull = true
            // Keep out open so its completion cannot flush the boundary batch before cancellation.
          }
        })
      setHandler(out,
        new OutHandler {
          override def onPull(): Unit =
            if (cancelOnPull) {
              // The boundary has consumed the preceding push and is asking for another element.
              cancel(in)
            } else pull(in)
        })
    }
  }

  /** Echoes the first element, answers the second with `response` and then cancels its input. */
  private class EchoThenRespondAndCancel(response: () => ByteString, drainStarted: TestProbe)
      extends GraphStage[FlowShape[ByteString, SslTlsOutbound]] {
    val in = Inlet[ByteString]("EchoThenRespondAndCancel.in")
    val out = Outlet[SslTlsOutbound]("EchoThenRespondAndCancel.out")
    override val shape = FlowShape(in, out)

    override def createLogic(attributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      private var first = true
      private var cancelOnPull = false
      setHandler(in,
        new InHandler {
          override def onPush(): Unit = {
            val bytes = grab(in)
            if (first) {
              first = false
              push(out, SendBytes(bytes))
            } else {
              push(out, SendBytes(response()))
              cancelOnPull = true
            }
          }
        })
      setHandler(out,
        new OutHandler {
          override def onPull(): Unit =
            if (cancelOnPull) cancel(in) // TLS accepted the response and asks for more
            else pull(in)

          override def onDownstreamFinish(cause: Throwable): Unit = {
            // TLS stops its plaintext input once it starts draining the accepted response.
            drainStarted.ref ! "drain started"
            super.onDownstreamFinish(cause)
          }
        })
    }
  }

  /** Drives a plain client `SSLEngine` against the ciphertext ports of a TLS operator. */
  private final class RawTlsClient(
      context: SSLContext,
      cipherIn: TestPublisher.Probe[ByteString],
      cipherOut: TestSubscriber.Probe[ByteString]) {
    val engine: SSLEngine = context.createSSLEngine()
    engine.setUseClientMode(true)
    engine.setEnabledProtocols(Array(context.getProtocol))

    private var netIn = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
    private var appIn = ByteBuffer.allocate(engine.getSession.getApplicationBufferSize)
    private var received = ByteString.empty
    private var outstandingDemand = 0L

    /** Ciphertext elements requested from the operator but not received yet. */
    def unusedDemand: Long = outstandingDemand

    def request(elements: Long): Unit = {
      cipherOut.request(elements)
      outstandingDemand += elements
    }

    def handshake(): Unit = {
      engine.beginHandshake()
      var status = engine.getHandshakeStatus
      while (status != NOT_HANDSHAKING && status != FINISHED) {
        status match {
          case NEED_WRAP   => send(ByteBuffer.allocate(0))
          case NEED_UNWRAP =>
            if (!unwrapBuffered()) receiveCiphertext()
          case NEED_TASK =>
            var task = engine.getDelegatedTask
            while (task ne null) { task.run(); task = engine.getDelegatedTask }
          case other => fail(s"unexpected handshake status $other")
        }
        status = engine.getHandshakeStatus
      }
    }

    def send(plaintext: ByteBuffer): Unit = {
      val packet = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
      val result = engine.wrap(plaintext, packet)
      result.getStatus shouldBe
      (if (engine.isOutboundDone) SSLEngineResult.Status.CLOSED
       else SSLEngineResult.Status.OK)
      packet.flip()
      cipherIn.sendNext(ByteString(packet))
    }

    /** Sends close_notify; TLS 1.3 keeps the inbound direction open. */
    def closeOutbound(): Unit = {
      engine.closeOutbound()
      send(ByteBuffer.allocate(0))
    }

    /** Reads ciphertext one element at a time until `expected` bytes of plaintext arrived. */
    def readPlaintext(expected: Int): ByteString = {
      while (received.size < expected) {
        if (!unwrapBuffered()) receiveCiphertext()
      }
      val result = received
      received = ByteString.empty
      result
    }

    /** Reads until the operator completes its ciphertext output. */
    def readUntilComplete(): ByteString = {
      request(Long.MaxValue - outstandingDemand)
      var completed = false
      while (!completed) {
        while (unwrapBuffered()) ()
        cipherOut.expectNextOrComplete() match {
          case Right(bytes) => append(bytes)
          case Left(_)      => completed = true
        }
      }
      while (unwrapBuffered()) ()
      val result = received
      received = ByteString.empty
      result
    }

    private def receiveCiphertext(): Unit = {
      if (outstandingDemand == 0) request(1)
      outstandingDemand -= 1
      append(cipherOut.expectNext())
    }

    private def append(bytes: ByteString): Unit = {
      if (netIn.remaining < bytes.size) {
        val bigger = ByteBuffer.allocate(netIn.position() + bytes.size)
        netIn.flip()
        bigger.put(netIn)
        netIn = bigger
      }
      bytes.copyToBuffer(netIn)
    }

    /** Unwraps one complete record from the buffered ciphertext, if there is one. */
    private def unwrapBuffered(): Boolean = {
      netIn.flip()
      val result = engine.unwrap(netIn, appIn)
      netIn.compact()
      result.getStatus match {
        case SSLEngineResult.Status.BUFFER_OVERFLOW =>
          val bigger = ByteBuffer.allocate(appIn.capacity * 2)
          appIn.flip()
          bigger.put(appIn)
          appIn = bigger
          true
        case SSLEngineResult.Status.BUFFER_UNDERFLOW => false
        case _                                       =>
          appIn.flip()
          received ++= ByteString(appIn)
          appIn.clear()
          result.bytesConsumed > 0 || result.bytesProduced > 0
      }
    }
  }

  "TlsGraphStage cancellation ordering" must {
    List("TLSv1.2", "TLSv1.3").foreach { protocol =>
      s"encrypt plaintext pushed before cancelling its output with $protocol" in {
        val context = TlsSpec.initSslContext(protocol)
        // Bypass the separate small-write timer regression covered by the deferred-close tests.
        val payload = ByteString("o" * (TlsGraphStage.MaxBatchedApplicationBytes + 1))
        val echo = Flow[SslTlsInbound]
          .collect { case SessionBytes(_, bytes) => bytes }
          .via(new EchoThenCancel)

        val (plainIn, plainOut) = TestSource[SslTlsOutbound]()
          .via(tls(context, client = true).atop(tls(context, client = false).reversed).join(echo))
          .collect { case SessionBytes(_, bytes) => bytes }
          // Exercise the normal interpreter ordering: push, next pull, cancellation, batch flush.
          .withAttributes(ActorAttributes.fuzzingMode(false))
          .toMat(TestSink[ByteString]())(Keep.both)
          .run()

        within(3.seconds) {
          // Complete a round trip before testing cancellation, including TLS 1.3 handshake output.
          plainOut.request(2)
          plainIn.sendNext(SendBytes(ByteString("ready")))
          plainOut.expectNext(ByteString("ready"))
          plainIn.sendNext(SendBytes(payload))
          plainOut.expectNext(payload)
          plainOut.expectComplete()
          plainIn.expectCancellation()
        }
      }
    }
  }

  /**
   * Lets the server accept a response that is larger than its remaining ciphertext demand, cancels the
   * server's plaintext output and then sends the client's close_notify. Returns the response and the
   * plaintext the client received before the server completed its ciphertext output.
   */
  private def halfCloseDuringDrain(protocol: String, closing: TLSClosing): (ByteString, ByteString) = {
    val context = TlsSpec.initSslContext(protocol)
    val drainStarted = TestProbe()
    val transportBufferSize = system.settings.config.getBytes("pekko.io.tcp.direct-buffer-size").toInt
    var response = ByteString.empty
    val handler = Flow[SslTlsInbound]
      .collect { case SessionBytes(_, bytes) => bytes }
      .via(new EchoThenRespondAndCancel(() => response, drainStarted))

    val (cipherIn, cipherOut) = TestSource[ByteString]()
      .via(tls(context, client = false, closing).reversed.join(handler))
      .toMat(TestSink[ByteString]())(Keep.both)
      .run()
    val client = new RawTlsClient(context, cipherIn, cipherOut)

    within(10.seconds) {
      // The server must be able to send its whole flight while the client sends handshake records.
      client.request(16)
      client.handshake()
      client.send(ByteBuffer.wrap("ready".getBytes(StandardCharsets.US_ASCII)))
      client.readPlaintext(5) shouldBe ByteString("ready")

      // Each ciphertext element holds at most one transport buffer, so the unused demand cannot
      // carry the last two buffers of the response before close_notify arrives.
      response = ByteString.fromArray(
        Array.tabulate[Byte]((client.unusedDemand.toInt + 2) * transportBufferSize)(_.toByte))
      client.send(ByteBuffer.wrap("go".getBytes(StandardCharsets.US_ASCII)))
      drainStarted.expectMsg("drain started")

      client.closeOutbound()
      val received = client.readUntilComplete()
      // The server answered close_notify with its own and stopped.
      client.engine.isInboundDone shouldBe true
      cipherIn.expectCancellation()
      (response, received)
    }
  }

  "TlsGraphStage cancellation drain" must {
    List(IgnoreComplete, EagerClose).foreach { closing =>
      s"send the accepted response after a TLS 1.3 peer half-closes during the drain with $closing" in {
        val (response, received) = halfCloseDuringDrain("TLSv1.3", closing)
        received.size shouldBe response.size
        (received == response) shouldBe true
      }

      s"stop draining when a TLS 1.2 peer closes during the drain with $closing" in {
        // A TLS 1.2 close_notify closes both directions of the SSLEngine.
        val (response, received) = halfCloseDuringDrain("TLSv1.2", closing)
        received.size should be < response.size
        (response.take(received.size) == received) shouldBe true
      }
    }
  }
}
