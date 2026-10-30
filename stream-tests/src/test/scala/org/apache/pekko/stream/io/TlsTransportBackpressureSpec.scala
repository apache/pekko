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
import pekko.NotUsed
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.{ TlsGraphStage, TlsModule }
import pekko.stream.scaladsl._
import pekko.stream.testkit.{ StreamSpec, TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit.WithLogCapturing
import pekko.util.ByteString

class TlsTransportBackpressureSpec extends AbstractTlsTransportBackpressureSpec(useLegacyActor = true)
class TlsGraphStageTransportBackpressureSpec extends AbstractTlsTransportBackpressureSpec(useLegacyActor = false)

abstract class AbstractTlsTransportBackpressureSpec(useLegacyActor: Boolean)
    extends StreamSpec(TlsSpec.configOverrides)
    with WithLogCapturing {

  private def serverTls(
      context: SSLContext): BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] = {
    val createEngine = () => {
      val engine = context.createSSLEngine()
      engine.setUseClientMode(false)
      engine.setEnabledProtocols(Array(context.getProtocol))
      engine
    }
    if (useLegacyActor)
      BidiFlow.fromGraph(TlsModule(Attributes.none, createEngine, _ => Success(()), IgnoreComplete))
    else
      BidiFlow
        .fromGraph(new TlsGraphStage(createEngine, _ => Success(()), IgnoreComplete))
        .withAttributes(TlsGraphStage.StreamTlsAttributes)
  }

  /** Drives a plain client `SSLEngine`, requesting server ciphertext only when it needs to unwrap. */
  private final class LazyTlsClient(
      context: SSLContext,
      cipherIn: TestPublisher.Probe[ByteString],
      cipherOut: TestSubscriber.Probe[ByteString]) {
    val engine: SSLEngine = context.createSSLEngine()
    engine.setUseClientMode(true)
    engine.setEnabledProtocols(Array(context.getProtocol))

    private var netIn = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
    private val appIn = ByteBuffer.allocate(engine.getSession.getApplicationBufferSize * 2)
    private var received = ByteString.empty

    def handshake(): Unit = {
      engine.beginHandshake()
      var status = engine.getHandshakeStatus
      while (status != NOT_HANDSHAKING && status != FINISHED) {
        status match {
          case NEED_WRAP   => send(ByteBuffer.allocate(0))
          case NEED_UNWRAP => if (!unwrapBuffered()) receiveCiphertext()
          case NEED_TASK   =>
            var task = engine.getDelegatedTask
            while (task ne null) { task.run(); task = engine.getDelegatedTask }
          case other => fail(s"unexpected handshake status $other")
        }
        status = engine.getHandshakeStatus
      }
    }

    def send(plaintext: ByteBuffer): Unit = {
      val packet = ByteBuffer.allocate(engine.getSession.getPacketBufferSize)
      engine.wrap(plaintext, packet).getStatus shouldBe SSLEngineResult.Status.OK
      packet.flip()
      cipherIn.sendNext(ByteString(packet))
    }

    def readPlaintext(expected: Int): ByteString = {
      while (received.size < expected)
        if (!unwrapBuffered()) receiveCiphertext()
      val result = received
      received = ByteString.empty
      result
    }

    private def receiveCiphertext(): Unit = {
      cipherOut.request(1)
      val bytes = cipherOut.expectNext()
      if (netIn.remaining < bytes.size) {
        val bigger = ByteBuffer.allocate(netIn.position() + bytes.size)
        netIn.flip()
        bigger.put(netIn)
        netIn = bigger
      }
      bytes.copyToBuffer(netIn)
    }

    private def unwrapBuffered(): Boolean = {
      netIn.flip()
      val result = engine.unwrap(netIn, appIn)
      netIn.compact()
      appIn.flip()
      received ++= ByteString(appIn)
      appIn.clear()
      result.getStatus == SSLEngineResult.Status.OK && (result.bytesConsumed > 0 || result.bytesProduced > 0)
    }
  }

  s"TLS server (${if (useLegacyActor) "legacy actor" else "GraphStage"})" must {
    List("TLSv1.2", "TLSv1.3").foreach { protocol =>
      s"wait for ciphertext demand when peer data arrives mid-flight with $protocol" in {
        val context = TlsSpec.initSslContext(protocol)
        val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => SendBytes(bytes): SslTlsOutbound }

        val (cipherIn, cipherOut) = TestSource[ByteString]()
          .via(serverTls(context).reversed.join(echo))
          .toMat(TestSink[ByteString]())(Keep.both)
          .run()
        val client = new LazyTlsClient(context, cipherIn, cipherOut)

        within(10.seconds) {
          client.handshake()
          client.send(ByteBuffer.wrap("hello".getBytes(StandardCharsets.US_ASCII)))
          client.readPlaintext(5) shouldBe ByteString("hello")
        }
      }
    }
  }
}
