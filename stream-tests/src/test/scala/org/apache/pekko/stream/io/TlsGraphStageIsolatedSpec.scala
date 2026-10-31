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
import java.util.concurrent.ThreadFactory
import javax.net.ssl._
import javax.net.ssl.SSLEngineResult.HandshakeStatus.{ FINISHED, NEED_UNWRAP, NEED_WRAP, NOT_HANDSHAKING }
import javax.net.ssl.SSLEngineResult.Status.{ BUFFER_OVERFLOW, BUFFER_UNDERFLOW, CLOSED, OK }

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Success

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.Cancellable
import pekko.event.LoggingAdapter
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.{ TlsGraphStage, TlsModule }
import pekko.stream.scaladsl._
import pekko.stream.scaladsl.GraphDSL.Implicits._
import pekko.stream.testkit.{ StreamSpec, TestPublisher, TestSubscriber }
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.stream.testkit.scaladsl.StreamTestKit.assertAllStagesStopped
import pekko.testkit.{ ExplicitlyTriggeredScheduler, WithLogCapturing }
import pekko.util.ByteString

/**
 * Focused [[TlsGraphStage]] tests for early failures, empty inputs, large
 * fragmented inputs, and TLS 1.2 renegotiation.
 */
class TlsGraphStageIsolatedSpec extends StreamSpec(TlsSpec.configOverrides) with WithLogCapturing {

  import TlsSpec._

  /** Constructs a [[BidiFlow]] backed by a single [[TlsGraphStage]] instance. */
  private def stageFlow(
      ctx: SSLContext,
      ciphers: Set[String],
      clientMode: Boolean,
      closing: TLSClosing,
      asyncBoundary: Boolean = true): BidiFlow[SslTlsOutbound, ByteString, ByteString, SslTlsInbound, NotUsed] = {
    val stage = new TlsGraphStage(
      () => {
        val engine = ctx.createSSLEngine()
        engine.setUseClientMode(clientMode)
        if (ciphers.nonEmpty) engine.setEnabledCipherSuites(ciphers.toArray)
        engine
      },
      _ => Success(()),
      closing)
    val flow = BidiFlow.fromGraph(stage)
    if (asyncBoundary) flow.addAttributes(TlsGraphStage.StreamTlsAttributes) else flow
  }

  /** Connects a client [[TlsGraphStage]] to a server [[TlsGraphStage]] in memory. */
  private def loopbackFlow(
      ctx: SSLContext,
      ciphers: Set[String],
      clientClosing: TLSClosing,
      serverClosing: TLSClosing,
      flow: Flow[SslTlsInbound, SslTlsOutbound, NotUsed]): Flow[SslTlsOutbound, SslTlsInbound, NotUsed] = {
    val client = stageFlow(ctx, ciphers, clientMode = true, clientClosing)
    val server = stageFlow(ctx, ciphers, clientMode = false, serverClosing)
    client.atop(server.reversed).join(flow)
  }

  private val echoApplicationFlow = Flow[SslTlsInbound].collect {
    case SessionBytes(_, b) => SendBytes(b)
  }

  private def roundTrip(
      ctx: SSLContext,
      inputs: Seq[ByteString],
      clientClosing: TLSClosing = IgnoreComplete,
      serverClosing: TLSClosing = IgnoreComplete,
      timeout: FiniteDuration = 20.seconds): ByteString = {
    val expectedBytes = inputs.foldLeft(0)(_ + _.size)
    val received =
      Source(inputs.map(SendBytes.apply).toList)
        .via(loopbackFlow(ctx, TLS12Ciphers, clientClosing, serverClosing, echoApplicationFlow))
        .collect { case SessionBytes(_, b) => b }

    if (expectedBytes == 0) {
      val outputs = Await.result(received.runWith(Sink.seq), timeout)
      outputs.foldLeft(ByteString.empty)(_ ++ _)
    } else {
      Await.result(
        received
          .scan(ByteString.empty)(_ ++ _)
          .drop(1)
          .filter(_.size >= expectedBytes)
          .runWith(Sink.head),
        timeout)
    }
  }

  "TlsGraphStage isolated cases" must {

    "reliably cancel subscriptions when cipherIn (TransportIn) fails early" in {
      val ex = new Exception("transport-in-failure")
      // asyncBoundary = false: this test exercises stage-level error propagation in
      // isolation and does not need a separate async island.
      val client = stageFlow(initSslContext("TLSv1.2"), TLS12Ciphers, clientMode = true, EagerClose,
        asyncBoundary = false)

      val (sub, out1, out2) =
        RunnableGraph
          .fromGraph(
            GraphDSL.createGraph(
              Source.asSubscriber[SslTlsOutbound],
              Sink.head[ByteString],
              Sink.head[SslTlsInbound])((_, _, _)) { implicit b => (s, o1, o2) =>
              val tls = b.add(client)
              s        ~> tls.in1
              tls.out1 ~> o1
              o2 <~ tls.out2
              tls.in2 <~ Source.failed(ex)
              ClosedShape
            })
          .run()

      the[Exception] thrownBy Await.result(out1, 3.seconds) should be(ex)
      the[Exception] thrownBy Await.result(out2, 3.seconds) should be(ex)
      val pub = TestPublisher.probe()
      pub.subscribe(sub)
      pub.expectSubscription().expectCancellation()
    }

    "reliably cancel subscriptions when plainIn (UserIn) fails early" in {
      val ex = new Exception("user-in-failure")
      // asyncBoundary = false: this test exercises stage-level error propagation in
      // isolation and does not need a separate async island.
      val client = stageFlow(initSslContext("TLSv1.2"), TLS12Ciphers, clientMode = true, EagerClose,
        asyncBoundary = false)

      val (sub, out1, out2) =
        RunnableGraph
          .fromGraph(
            GraphDSL.createGraph(
              Source.asSubscriber[ByteString],
              Sink.head[ByteString],
              Sink.head[SslTlsInbound])((_, _, _)) { implicit b => (s, o1, o2) =>
              val tls = b.add(client)
              Source.failed[SslTlsOutbound](ex) ~> tls.in1
              tls.out1                          ~> o1
              o2 <~ tls.out2
              tls.in2 <~ s
              ClosedShape
            })
          .run()

      the[Exception] thrownBy Await.result(out1, 3.seconds) should be(ex)
      the[Exception] thrownBy Await.result(out2, 3.seconds) should be(ex)
      val pub = TestPublisher.probe()
      pub.subscribe(sub)
      pub.expectSubscription().expectCancellation()
    }

    "round-trip alternating empty and non-empty ByteString inputs exactly" in {
      val input = List(
        ByteString.empty,
        ByteString("A"),
        ByteString.empty,
        ByteString("BC"),
        ByteString.empty,
        ByteString("DEF"),
        ByteString.empty)
      val expected = input.foldLeft(ByteString.empty)(_ ++ _)

      roundTrip(initSslContext("TLSv1.2"), input) shouldEqual expected
    }

    "round-trip a fragmented large payload exactly" in {
      val payloadSize = (64 * 1024) + 1
      val payload = ByteString(Array.tabulate[Byte](payloadSize)(i => (i % 251).toByte))

      roundTrip(initSslContext("TLSv1.2"), List(payload), timeout = 30.seconds) shouldEqual payload
    }

    "complete without payload when both sides are EagerClose" in {
      roundTrip(
        initSslContext("TLSv1.2"),
        Nil,
        clientClosing = EagerClose,
        serverClosing = EagerClose,
        timeout = 10.seconds) shouldEqual ByteString.empty
    }

    "complete when plainIn finishes immediately under EagerClose/IgnoreComplete" in {
      roundTrip(
        initSslContext("TLSv1.2"),
        Nil,
        clientClosing = EagerClose,
        serverClosing = IgnoreComplete,
        timeout = 10.seconds) shouldEqual ByteString.empty
    }

    "pass data before and after NegotiateNewSession (TLS 1.2 renegotiation)" in {
      val renegotiationContext = initSslContext("TLSv1.2")
      val markNewSession = Flow[SslTlsInbound].map {
        var session: SSLSession = null

        {
          case SessionTruncated                      => SendBytes(ByteString("TRUNCATED"))
          case SessionBytes(s, b) if session eq null =>
            session = s
            SendBytes(b)
          case SessionBytes(s, b) if s != session =>
            session = s
            SendBytes(ByteString("NEWSESSION") ++ b)
          case SessionBytes(_, b) => SendBytes(b)
        }
      }

      val expected = ByteString("helloNEWSESSIONworld")
      val outputs = Await.result(
        Source(List[SslTlsOutbound](SendBytes(ByteString("hello")), NegotiateNewSession,
          SendBytes(ByteString("world"))))
          .via(loopbackFlow(renegotiationContext, TLS12Ciphers, IgnoreComplete, IgnoreComplete, markNewSession))
          .collect { case SessionBytes(_, b) => b }
          .takeWithin(10.seconds)
          .runWith(Sink.seq),
        20.seconds)

      outputs.foldLeft(ByteString.empty)(_ ++ _) shouldEqual expected
    }
  }
}

class TlsGraphStageSmallBufferSpec
    extends StreamSpec(
      ConfigFactory.parseString("pekko.io.tcp.direct-buffer-size = 8192")
        .withFallback(ConfigFactory.parseString(TlsSpec.configOverrides))) {

  "TlsGraphStage" must {
    for (protocol <- Seq("TLSv1.2", "TLSv1.3")) {
      s"round-trip $protocol records larger than the TCP pool buffers" in {
        assertAllStagesStopped {
          val context = TlsSpec.initSslContext(protocol)
          def tls(client: Boolean) =
            BidiFlow.fromGraph(new TlsGraphStage(
              () => {
                val engine = context.createSSLEngine()
                engine.setUseClientMode(client)
                engine.setEnabledProtocols(Array(protocol))
                engine
              },
              _ => Success(()),
              IgnoreComplete)).withAttributes(TlsGraphStage.StreamTlsAttributes)

          val payload = ByteString.fromArray(Array.tabulate[Byte](32768)(_.toByte))
          val echo = Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => SendBytes(bytes) }
          val received = Source.single[SslTlsOutbound](SendBytes(payload))
            .via(tls(client = true).atop(tls(client = false).reversed).join(echo))
            .collect { case SessionBytes(_, bytes) => bytes }
            .scan(ByteString.empty)(_ ++ _)
            .filter(_.size >= payload.size)
            .runWith(Sink.head)

          Await.result(received, remainingOrDefault) shouldBe payload
        }

        // TLS fallback buffers must not enter the shared, fixed-size TCP pool.
        val pool = pekko.io.Tcp(system).bufferPool
        val buffer = pool.acquire()
        try {
          buffer.isDirect shouldBe true
          buffer.capacity() shouldBe 8192
        } finally pool.release(buffer)
      }
    }
  }
}

abstract class TlsGraphStageBufferTestBase(config: Config) extends StreamSpec(config) {

  protected val TlsRecord = ByteString(Array[Byte](23, 3, 3, 0, 0))
  protected val ApplicationCiphertext = ByteString(0x5A.toByte)
  protected val CloseCiphertext = ByteString(0x5B.toByte)
  protected val HandshakeCiphertext = ByteString(0x5C.toByte)

  protected final class ScriptedSSLEngine(
      delegate: SSLEngine,
      wrapOnCloseInbound: Boolean,
      requestHandshakeAfterApplication: Boolean,
      requestWrapOnSubsequentUnwrap: Boolean,
      @volatile var requiredOutputSpace: Int,
      negotiatedPacketSize: Int,
      negotiatedApplicationSize: Int,
      sessionProtocol: String = null,
      wrapHandshakeOnNeedWrap: Boolean = false) extends SSLEngine {
    private var handshakeStatus: SSLEngineResult.HandshakeStatus = NEED_UNWRAP
    @volatile var unwrapCount = 0
    private var inboundDone = false
    @volatile private var outboundDone = false
    private var handshakePending = true

    @volatile var applicationWraps = 0
    @volatile var applicationBytes = 0
    @volatile var handshakesStarted = 0
    @volatile var closeInboundCalls = 0
    @volatile var unwrapFailure: SSLException = _
    @volatile var failedUnwraps = 0
    @volatile var overflowWraps = 0
    @volatile var largestOutputCapacity = 0
    @volatile var inputArray: Array[Byte] = _
    @volatile var outputArray: Array[Byte] = _
    @volatile var underflowUnwraps = 0
    @volatile var overflowUnwraps = 0
    // Keep the scripted session's initial requirements independent of the JDK provider.
    private var reportedPacketSize = 8192
    private var reportedApplicationSize = 4096
    private var consecutiveBufferRetries = 0
    @volatile var sessionQueries = 0
    private lazy val reportedSession = java.lang.reflect.Proxy.newProxyInstance(
      classOf[SSLSession].getClassLoader,
      Array[Class[?]](classOf[SSLSession]),
      (_: AnyRef, method: java.lang.reflect.Method, args: Array[AnyRef]) =>
        if (method.getName == "getPacketBufferSize") Int.box(reportedPacketSize)
        else if (method.getName == "getApplicationBufferSize") Int.box(reportedApplicationSize)
        else if (method.getName == "getProtocol" && (sessionProtocol ne null)) sessionProtocol
        else method.invoke(delegate.getSession, Option(args).getOrElse(Array.empty[AnyRef]): _*))
      .asInstanceOf[SSLSession]

    override def beginHandshake(): Unit = {
      handshakesStarted += 1
      handshakePending = true
      handshakeStatus = NEED_UNWRAP
    }

    override def getHandshakeStatus: SSLEngineResult.HandshakeStatus = handshakeStatus

    override def wrap(
        srcs: Array[ByteBuffer],
        offset: Int,
        length: Int,
        dst: ByteBuffer): SSLEngineResult = {
      largestOutputCapacity = math.max(largestOutputCapacity, dst.capacity())
      if (dst.hasArray) outputArray = dst.array()
      if (dst.remaining() < requiredOutputSpace) {
        overflowWraps += 1
        return new SSLEngineResult(BUFFER_OVERFLOW, handshakeStatus, 0, 0)
      }
      if (wrapHandshakeOnNeedWrap && handshakeStatus == NEED_WRAP && !(wrapOnCloseInbound && inboundDone)) {
        // Like the JDK, a wrap while NEED_WRAP produces only the handshake message and consumes no plaintext.
        // SunJSSE reports FINISHED for a TLS 1.3 KeyUpdate response, then NOT_HANDSHAKING from the engine.
        dst.put(HandshakeCiphertext.head)
        handshakeStatus = NOT_HANDSHAKING
        return new SSLEngineResult(OK, FINISHED, 0, 1)
      }
      var consumed = 0
      var index = offset
      while (index < offset + length) {
        val src = srcs(index)
        consumed += src.remaining()
        src.position(src.limit())
        index += 1
      }

      val closeWrap = wrapOnCloseInbound && inboundDone && handshakeStatus == NEED_WRAP
      val produced =
        if (closeWrap) {
          dst.put(CloseCiphertext.head)
          outboundDone = true
          1
        } else if (consumed > 0) {
          applicationWraps += 1
          applicationBytes += consumed
          dst.put(ApplicationCiphertext.head)
          1
        } else 0

      handshakeStatus =
        if (consumed > 0 && requestHandshakeAfterApplication) NEED_UNWRAP else NOT_HANDSHAKING
      new SSLEngineResult(if (closeWrap) CLOSED else OK, handshakeStatus, consumed, produced)
    }

    override def unwrap(
        src: ByteBuffer,
        dsts: Array[ByteBuffer],
        offset: Int,
        length: Int): SSLEngineResult = {
      if (unwrapFailure ne null) {
        failedUnwraps += 1
        throw unwrapFailure
      }
      if (src.hasArray) inputArray = src.array()
      if (!handshakePending && negotiatedPacketSize > 0) {
        reportedPacketSize = negotiatedPacketSize
        if (negotiatedApplicationSize > 0) reportedApplicationSize = negotiatedApplicationSize
        consecutiveBufferRetries += 1
        if (consecutiveBufferRetries > 8) throw new SSLException("TLS buffers were not resized after repeated retries")
        if (dsts(offset).remaining() < negotiatedApplicationSize) {
          overflowUnwraps += 1
          return new SSLEngineResult(BUFFER_OVERFLOW, NOT_HANDSHAKING, 0, 0)
        }
        if (src.remaining() < negotiatedPacketSize) {
          underflowUnwraps += 1
          return new SSLEngineResult(BUFFER_UNDERFLOW, NOT_HANDSHAKING, 0, 0)
        }
      }
      consecutiveBufferRetries = 0
      val consumed = src.remaining()
      src.position(src.limit())
      unwrapCount += 1

      if (handshakePending) {
        handshakePending = false
        handshakeStatus = NOT_HANDSHAKING
        new SSLEngineResult(OK, FINISHED, consumed, 0)
      } else if (negotiatedPacketSize > 0) {
        val produced = math.max(1, negotiatedApplicationSize)
        dsts(offset).put(Array.fill[Byte](produced)(0x41.toByte))
        new SSLEngineResult(OK, NOT_HANDSHAKING, consumed, produced)
      } else if (requestWrapOnSubsequentUnwrap) {
        handshakeStatus = NEED_WRAP
        new SSLEngineResult(OK, NEED_WRAP, consumed, 0)
      } else {
        handshakeStatus = NOT_HANDSHAKING
        new SSLEngineResult(OK, NOT_HANDSHAKING, consumed, 0)
      }
    }

    override def closeInbound(): Unit = {
      closeInboundCalls += 1
      inboundDone = true
      handshakeStatus = if (wrapOnCloseInbound) NEED_WRAP else NOT_HANDSHAKING
    }

    override def closeOutbound(): Unit = {
      outboundDone = true
      handshakeStatus = NOT_HANDSHAKING
    }

    override def isInboundDone: Boolean = inboundDone
    override def isOutboundDone: Boolean = outboundDone
    override def getDelegatedTask: Runnable = null
    override def getSession: SSLSession = {
      sessionQueries += 1
      if (negotiatedPacketSize > 0 || (sessionProtocol ne null)) reportedSession else delegate.getSession
    }
    override def getSupportedCipherSuites: Array[String] = delegate.getSupportedCipherSuites
    override def getEnabledCipherSuites: Array[String] = delegate.getEnabledCipherSuites
    override def setEnabledCipherSuites(suites: Array[String]): Unit = delegate.setEnabledCipherSuites(suites)
    override def getSupportedProtocols: Array[String] = delegate.getSupportedProtocols
    override def getEnabledProtocols: Array[String] = delegate.getEnabledProtocols
    override def setEnabledProtocols(protocols: Array[String]): Unit = delegate.setEnabledProtocols(protocols)
    override def setUseClientMode(mode: Boolean): Unit = delegate.setUseClientMode(mode)
    override def getUseClientMode: Boolean = delegate.getUseClientMode
    override def setNeedClientAuth(need: Boolean): Unit = delegate.setNeedClientAuth(need)
    override def getNeedClientAuth: Boolean = delegate.getNeedClientAuth
    override def setWantClientAuth(want: Boolean): Unit = delegate.setWantClientAuth(want)
    override def getWantClientAuth: Boolean = delegate.getWantClientAuth
    override def setEnableSessionCreation(flag: Boolean): Unit = delegate.setEnableSessionCreation(flag)
    override def getEnableSessionCreation: Boolean = delegate.getEnableSessionCreation
  }

  protected final class DeferredCloseHarness(
      val engine: ScriptedSSLEngine,
      val sessionVerified: Promise[Unit],
      val plainIn: TestPublisher.Probe[SslTlsOutbound],
      val cipherIn: TestPublisher.Probe[ByteString],
      val cipherOut: TestSubscriber.Probe[ByteString],
      val plainOut: TestSubscriber.Probe[SslTlsInbound])

  protected def newHarness(
      wrapOnCloseInbound: Boolean = false,
      closing: TLSClosing = IgnoreComplete,
      requestHandshakeAfterApplication: Boolean = false,
      requestWrapOnSubsequentUnwrap: Boolean = true,
      extraAttributes: Attributes = Attributes.none,
      requiredOutputSpace: Int = 0,
      negotiatedPacketSize: Int = 0,
      negotiatedApplicationSize: Int = 0,
      sessionProtocol: String = null,
      wrapHandshakeOnNeedWrap: Boolean = false,
      useLegacyActor: Boolean = false): DeferredCloseHarness = {
    val engine =
      new ScriptedSSLEngine(TlsSpec.initSslContext("TLSv1.2").createSSLEngine(), wrapOnCloseInbound,
        requestHandshakeAfterApplication, requestWrapOnSubsequentUnwrap,
        requiredOutputSpace, negotiatedPacketSize, negotiatedApplicationSize, sessionProtocol,
        wrapHandshakeOnNeedWrap)
    val sessionVerified = Promise[Unit]()
    val verifySession = (_: SSLSession) => {
      sessionVerified.trySuccess(())
      Success(())
    }
    val tlsFlow =
      if (useLegacyActor)
        BidiFlow.fromGraph(TlsModule(Attributes.none, () => engine, verifySession, closing))
      else
        BidiFlow.fromGraph(new TlsGraphStage(() => engine, verifySession, closing))
    val probes =
      RunnableGraph
        .fromGraph(
          GraphDSL.createGraph(
            TestSource[SslTlsOutbound](),
            TestSource[ByteString](),
            TestSink[ByteString](),
            TestSink[SslTlsInbound]())(Tuple4.apply) { implicit b => (plainIn, cipherIn, cipherOut, plainOut) =>
            val tls = b.add(
              tlsFlow.withAttributes(Attributes.inputBuffer(1, 1).and(extraAttributes)))
            plainIn                                          ~> tls.in1
            tls.out1                                         ~> cipherOut
            cipherIn                                         ~> tls.in2
            if (extraAttributes == Attributes.none) tls.out2 ~> plainOut
            else {
              // Keep the subscriber boundary's cancellation independent of the TLS stage's attributes.
              tls.out2 ~> Flow[SslTlsInbound].map(identity) ~> plainOut
            }
            ClosedShape
          })
        .run()

    val harness = new DeferredCloseHarness(engine, sessionVerified, probes._1, probes._2, probes._3, probes._4)
    harness.plainIn.expectRequest()
    harness.cipherIn.expectRequest()
    harness.cipherOut.ensureSubscription()
    harness.plainOut.ensureSubscription()
    harness
  }

  protected def finishInitialHandshake(harness: DeferredCloseHarness): Unit = {
    harness.plainOut.request(1)
    harness.cipherIn.sendNext(TlsRecord)
    harness.cipherIn.expectRequest()
    awaitAssert(harness.engine.unwrapCount shouldBe 1)
    // unwrapCount is incremented before the stage processes FINISHED and queries the session.
    Await.result(harness.sessionVerified.future, remainingOrDefault)
  }

}

class TlsGraphStageDeferredCloseSpec extends TlsGraphStageBufferTestBase(TlsGraphStageDeferredCloseSpec.config) {

  "TlsGraphStage deferred close handling" must {

    for (closing <- Seq(IgnoreComplete, EagerClose)) {
      s"flush accepted plaintext on output cancellation with $closing before the batch timer fires" in {
        val harness = newHarness(closing = closing)
        finishInitialHandshake(harness)

        harness.plainIn.sendNext(SendBytes(ByteString("a")))
        harness.plainIn.expectRequest() // the stage owns the bytes, but the batch timer has not run
        harness.plainOut.cancel()
        harness.plainIn.expectCancellation()

        // Cancellation must stop new input without discarding the accepted batch or bypassing backpressure.
        harness.engine.applicationWraps shouldBe 0
        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        harness.cipherOut.expectComplete()
        harness.cipherIn.expectCancellation()
        harness.engine.applicationBytes shouldBe 1
      }

      s"grow the transport buffer while draining accepted plaintext on output cancellation with $closing" in {
        val poolBufferSize = pekko.io.Tcp(system).Settings.DirectBufferSize
        val harness = newHarness(closing = closing, requiredOutputSpace = poolBufferSize + 1)
        finishInitialHandshake(harness)
        harness.plainIn.sendNext(SendBytes(ByteString("a")))
        harness.plainIn.expectRequest()
        harness.plainOut.cancel()
        harness.plainIn.expectCancellation()

        harness.engine.applicationWraps shouldBe 0
        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        harness.cipherOut.expectComplete()
        harness.cipherIn.expectCancellation()
        harness.engine.applicationBytes shouldBe 1
        harness.engine.overflowWraps shouldBe 1
        harness.engine.largestOutputCapacity should be > poolBufferSize
      }

      s"drain a partially sent large message on output cancellation with $closing" in {
        val harness = newHarness(closing = closing)
        finishInitialHandshake(harness)
        // The scripted engine consumes one input buffer per wrap. Keep a tail in the accepted ByteString
        // so draining must refill that buffer on later transport requests; TLS record boundaries are not modeled.
        val applicationBuffers = TlsGraphStage.MaxApplicationRecordsPerEngineCall * 2 + 1
        val payload = ByteString("a" * (harness.engine.getSession.getApplicationBufferSize * applicationBuffers))
        harness.plainIn.sendNext(SendBytes(payload))
        harness.plainIn.expectRequest()

        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        val initiallySent = harness.engine.applicationBytes
        initiallySent should be > 0
        initiallySent should be < payload.size

        harness.plainOut.cancel()
        harness.plainIn.expectCancellation()
        // Processing cancellation must not wrap more plaintext without transport demand.
        harness.engine.applicationBytes shouldBe initiallySent

        var sent = initiallySent
        while (sent < payload.size) {
          harness.cipherOut.request(1)
          harness.cipherOut.expectNext(ApplicationCiphertext)
          val nextSent = harness.engine.applicationBytes
          nextSent should be > sent
          nextSent should be <= payload.size
          sent = nextSent
        }
        // The last data element consumed the demand; allow the outbound close to run as well.
        harness.cipherOut.request(1)
        harness.cipherOut.expectComplete()
        harness.cipherIn.expectCancellation()
        harness.engine.applicationBytes shouldBe payload.size
      }
    }

    "close without waiting for peer handshake input while draining cancelled plaintext" in {
      val harness = newHarness(requestHandshakeAfterApplication = true)
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.plainOut.cancel()
      harness.plainIn.expectCancellation()

      harness.cipherOut.request(1)
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.cipherOut.expectComplete()
      harness.cipherIn.expectCancellation()
      harness.engine.applicationBytes shouldBe 1
    }

    // The scripted engine answers the first post-handshake unwrap with NEED_WRAP, like a received
    // TLS 1.3 KeyUpdate that requests an update, and wraps the response before any plaintext.
    for (keyUpdateBeforeCancel <- Seq(true, false)) {
      val order = if (keyUpdateBeforeCancel) "before" else "after"
      s"keep draining accepted plaintext after a TLS 1.3 KeyUpdate received $order output cancellation" in {
        val harness = newHarness(sessionProtocol = "TLSv1.3", wrapHandshakeOnNeedWrap = true)
        finishInitialHandshake(harness)
        harness.plainIn.sendNext(SendBytes(ByteString("a")))
        harness.plainIn.expectRequest()
        if (keyUpdateBeforeCancel) {
          harness.cipherIn.sendNext(TlsRecord)
          awaitAssert(harness.engine.unwrapCount shouldBe 2)
        }
        harness.plainOut.cancel()
        harness.plainIn.expectCancellation()
        if (!keyUpdateBeforeCancel) {
          harness.cipherIn.sendNext(TlsRecord)
          awaitAssert(harness.engine.unwrapCount shouldBe 2)
        }

        // A KeyUpdate response is not a new handshake, so it must not discard the accepted plaintext.
        val sent = harness.cipherOut.toStrict(remainingOrDefault).foldLeft(ByteString.empty)(_ ++ _)
        sent shouldBe HandshakeCiphertext ++ ApplicationCiphertext
        harness.engine.applicationBytes shouldBe 1
      }
    }

    "stop draining when a TLS 1.2 engine needs to wrap during output cancellation" in {
      val harness = newHarness(sessionProtocol = "TLSv1.2", wrapHandshakeOnNeedWrap = true)
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.cipherIn.sendNext(TlsRecord)
      awaitAssert(harness.engine.unwrapCount shouldBe 2)
      harness.plainOut.cancel()
      harness.plainIn.expectCancellation()

      // TLS 1.2 NEED_WRAP may start a renegotiation, so draining must stop instead of waiting for it.
      harness.cipherOut.toStrict(remainingOrDefault) shouldBe empty
      harness.engine.applicationBytes shouldBe 0
    }

    "stop draining when the transport output is cancelled" in {
      val harness = newHarness()
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.plainOut.cancel()
      harness.plainIn.expectCancellation()

      harness.cipherOut.cancel()
      harness.cipherIn.expectCancellation()
      harness.engine.applicationWraps shouldBe 0
    }

    "complete without truncation when the transport output is cancelled before the initial handshake" in {
      val harness = newHarness()

      // No port has received an element or demand that would otherwise start the pump.
      harness.cipherOut.cancel()

      harness.plainIn.expectCancellation()
      harness.cipherIn.expectCancellation()
      harness.plainOut.expectComplete()
      harness.engine.unwrapCount shouldBe 0
      harness.engine.closeInboundCalls shouldBe 0
    }

    "report transport output cancellation without closing the established engine inbound" in {
      val harness = newHarness()
      finishInitialHandshake(harness)

      harness.cipherOut.cancel()

      harness.plainIn.expectCancellation()
      harness.cipherIn.expectCancellation()
      harness.plainOut.expectNext(SessionTruncated)
      harness.plainOut.expectComplete()
      harness.engine.closeInboundCalls shouldBe 0
    }

    "report transport output cancellation after the engine outbound has closed" in {
      val harness = newHarness(closing = EagerClose)
      finishInitialHandshake(harness)

      harness.plainIn.sendComplete()
      harness.cipherOut.request(1)
      awaitAssert(harness.engine.isOutboundDone shouldBe true)
      harness.cipherOut.cancel()

      harness.cipherIn.expectCancellation()
      harness.plainOut.expectNext(SessionTruncated)
      harness.plainOut.expectComplete()
      harness.engine.closeInboundCalls shouldBe 0
    }

    for {
      useLegacyActor <- List(false, true)
      bufferedInput <- List(false, true)
    } {
      val engine = if (useLegacyActor) "legacy actor" else "graph stage"
      s"report cancellation after transport input completes without closing inbound ($engine, buffered=$bufferedInput)" in {
        assertAllStagesStopped {
          val harness = newHarness(
            negotiatedPacketSize = TlsRecord.size,
            negotiatedApplicationSize = 1,
            useLegacyActor = useLegacyActor)
          finishInitialHandshake(harness)

          // Consume the handshake's remaining plaintext demand before completing transport input.
          harness.cipherIn.sendNext(TlsRecord)
          harness.plainOut.expectNext().asInstanceOf[SessionBytes].bytes shouldBe ByteString("A")
          if (bufferedInput) harness.cipherIn.sendNext(TlsRecord)
          // These probe calls enqueue completion, cancellation, then demand in that order.
          harness.cipherIn.sendComplete()
          harness.cipherOut.cancel()
          // With nothing left to decrypt, teardown releases the plaintext input without waiting for demand.
          if (!bufferedInput) harness.plainIn.expectCancellation()
          harness.plainOut.request(2)

          if (bufferedInput)
            harness.plainOut.expectNext().asInstanceOf[SessionBytes].bytes shouldBe ByteString("A")
          harness.plainOut.expectNext(SessionTruncated)
          harness.plainOut.expectComplete()
          if (bufferedInput) harness.plainIn.expectCancellation()
          harness.engine.closeInboundCalls shouldBe 0
        }
      }
    }

    for (useLegacyActor <- List(false, true)) {
      val engine = if (useLegacyActor) "legacy actor" else "graph stage"
      s"preserve an unwrap failure during transport output cancellation ($engine)" in {
        assertAllStagesStopped {
          val harness = newHarness(
            negotiatedPacketSize = TlsRecord.size,
            negotiatedApplicationSize = 1,
            useLegacyActor = useLegacyActor)
          finishInitialHandshake(harness)
          harness.cipherIn.sendNext(TlsRecord)
          harness.plainOut.expectNext().asInstanceOf[SessionBytes].bytes shouldBe ByteString("A")

          val failure = new SSLException("failed final inbound round")
          harness.engine.unwrapFailure = failure
          harness.cipherIn.sendNext(TlsRecord)
          harness.cipherOut.cancel()
          harness.plainOut.request(1)

          harness.plainOut.expectError() shouldBe failure
          harness.plainIn.expectCancellation()
          harness.cipherIn.expectCancellation()
          harness.engine.failedUnwraps shouldBe 1
        }
      }
    }

    for (closing <- Seq(IgnoreComplete, EagerClose)) {
      s"drain accepted plaintext when transport input completes during cancellation with $closing" in {
        val harness = newHarness(closing = closing)
        finishInitialHandshake(harness)
        harness.plainIn.sendNext(SendBytes(ByteString("a")))
        harness.plainIn.expectRequest()
        harness.plainOut.cancel()
        harness.plainIn.expectCancellation()
        harness.cipherIn.sendComplete()

        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        harness.cipherOut.expectComplete()
        harness.engine.applicationBytes shouldBe 1
      }
    }

    for (closing <- Seq(IgnoreCancel, IgnoreBoth)) {
      s"terminate when both outputs are cancelled with $closing and silent transport input" in {
        val harness = newHarness(closing = closing)
        finishInitialHandshake(harness)
        harness.plainOut.cancel()
        harness.cipherOut.cancel()

        harness.cipherIn.expectCancellation()
        harness.plainIn.expectCancellation()
      }
    }

    "fail transport when upstream fails during delayed cancellation" in {
      val harness = newHarness(requestWrapOnSubsequentUnwrap = false,
        extraAttributes = Attributes(
          Attributes.CancellationStrategy(
            Attributes.CancellationStrategy.AfterDelay(1.second, Attributes.CancellationStrategy.PropagateFailure))))
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.plainOut.cancel()
      // A second signal from the cancelled output proves that its cancellation was processed.
      harness.cipherIn.sendNext(TlsRecord)
      harness.cipherIn.expectRequest()
      awaitAssert(harness.engine.unwrapCount shouldBe 2)
      val failure = new RuntimeException("plaintext input failed during cancellation")
      harness.plainIn.sendError(failure)

      harness.cipherOut.expectError() shouldBe failure
      system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler].timePasses(1.second)
      harness.cipherIn.expectCancellation()
    }

    "preserve the accepted batch when upstream completes during delayed cancellation" in {
      val harness = newHarness(extraAttributes = Attributes(
        Attributes.CancellationStrategy(
          Attributes.CancellationStrategy.AfterDelay(1.second, Attributes.CancellationStrategy.PropagateFailure))))
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()

      harness.plainOut.cancel()
      harness.plainIn.sendComplete()
      harness.cipherOut.request(1)
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.cipherOut.expectComplete()
      harness.engine.applicationBytes shouldBe 1
      system.scheduler.asInstanceOf[ExplicitlyTriggeredScheduler].timePasses(1.second)
      harness.cipherIn.expectCancellation()
    }

    "close promptly on output cancellation during the initial handshake" in {
      val harness = newHarness()
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.plainOut.cancel()

      harness.plainIn.expectCancellation()
      harness.cipherIn.expectCancellation()
      harness.cipherOut.expectComplete()
      harness.engine.applicationWraps shouldBe 0
    }

    "drain only the current session before a queued renegotiation on output cancellation" in {
      val harness = newHarness()
      finishInitialHandshake(harness)

      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.plainIn.sendNext(NegotiateNewSession)
      harness.plainIn.expectRequest()
      harness.plainIn.sendNext(SendBytes(ByteString("b")))
      harness.plainIn.expectRequest()
      harness.plainOut.cancel()
      harness.plainIn.expectCancellation()

      harness.cipherOut.request(1)
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.cipherOut.expectComplete()
      harness.cipherIn.expectCancellation()
      harness.engine.applicationBytes shouldBe 1
      harness.engine.handshakesStarted shouldBe 1
    }

    "complete when inbound closes before a deferred user batch runs" in {
      val harness = newHarness()
      finishInitialHandshake(harness)

      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest() // proves onPush ran before cipherIn completion
      harness.cipherIn.sendComplete()

      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }

    "flush deferred cipher bytes before completing after inbound close" in {
      val harness = newHarness()
      finishInitialHandshake(harness)

      harness.cipherIn.sendNext(TlsRecord) // make the small user input bypass the user batch timer
      harness.cipherIn.expectRequest()
      awaitAssert(harness.engine.unwrapCount shouldBe 2)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.cipherOut.request(1)
      awaitAssert(harness.engine.applicationWraps shouldBe 1)
      harness.cipherOut.expectNoMessage(100.millis)

      harness.cipherIn.sendComplete()

      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }

    "wrap pending engine output before completing after inbound close" in {
      val harness = newHarness(wrapOnCloseInbound = true)
      finishInitialHandshake(harness)

      harness.cipherOut.request(1)
      harness.cipherIn.sendComplete()

      harness.cipherOut.expectNext(CloseCiphertext)
      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }

    "grow an empty transport buffer and return only unique pooled buffers after termination" in {
      val tcp = pekko.io.Tcp(system)
      val poolBufferSize = tcp.Settings.DirectBufferSize
      assertAllStagesStopped {
        val harness = newHarness(requiredOutputSpace = poolBufferSize + 1)
        finishInitialHandshake(harness)

        harness.plainIn.sendNext(SendBytes(ByteString.fromArray(Array.fill[Byte](2048)(1))))
        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        harness.engine.overflowWraps shouldBe 1
        harness.engine.largestOutputCapacity should be > poolBufferSize

        harness.cipherIn.sendComplete()
        harness.cipherOut.expectComplete()
        harness.plainOut.expectComplete()
        harness.plainIn.expectCancellation()
      }

      // Hold the entire free list at once to detect oversized buffers and duplicate releases.
      val buffers = Vector.fill(tcp.Settings.MaxDirectBufferPoolSize)(tcp.bufferPool.acquire())
      try {
        buffers.foreach { buffer =>
          buffer.isDirect shouldBe true
          buffer.capacity() shouldBe poolBufferSize
        }
        for (i <- buffers.indices; j <- 0 until i)
          (buffers(i) eq buffers(j)) shouldBe false
      } finally buffers.foreach(tcp.bufferPool.release)
    }

    "return to a pooled output buffer after a handshake lowers the capacity requirement" in {
      val poolBufferSize = pekko.io.Tcp(system).Settings.DirectBufferSize
      val harness = newHarness(requiredOutputSpace = poolBufferSize + 1)
      finishInitialHandshake(harness)
      val payload = SendBytes(ByteString.fromArray(Array.fill[Byte](2048)(1)))

      for (_ <- 1 to 2) {
        harness.plainIn.sendNext(payload)
        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
      }
      // A real empty-buffer overflow sets a lower bound until the next completed handshake.
      harness.engine.overflowWraps shouldBe 1
      harness.engine.largestOutputCapacity should be > poolBufferSize
      harness.engine.requiredOutputSpace = 0
      harness.engine.largestOutputCapacity = 0

      harness.cipherOut.request(1)
      harness.plainIn.sendNext(NegotiateNewSession)
      awaitAssert(harness.engine.handshakesStarted shouldBe 2)
      harness.cipherIn.sendNext(TlsRecord)
      harness.cipherIn.expectRequest()
      awaitAssert(harness.engine.unwrapCount shouldBe 2)
      harness.engine.largestOutputCapacity = 0
      harness.plainIn.sendNext(payload)
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.engine.largestOutputCapacity shouldBe poolBufferSize
      harness.engine.overflowWraps shouldBe 1

      harness.cipherIn.sendComplete()
      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }

    "flush deferred cipher bytes before retrying an overflow without growing the buffer" in {
      val poolBufferSize = system.settings.config.getBytes("pekko.io.tcp.direct-buffer-size").toInt
      val harness = newHarness(requiredOutputSpace = poolBufferSize)
      finishInitialHandshake(harness)

      harness.cipherIn.sendNext(TlsRecord) // bypass the user batch timer for the first write
      harness.cipherIn.expectRequest()
      awaitAssert(harness.engine.unwrapCount shouldBe 2)
      harness.plainIn.sendNext(SendBytes(ByteString("a")))
      harness.plainIn.expectRequest()
      harness.cipherOut.request(1)
      awaitAssert(harness.engine.applicationWraps shouldBe 1)

      // The overflow is caused by buffered ciphertext, not an undersized empty buffer.
      harness.plainIn.sendNext(SendBytes(ByteString.fromArray(Array.fill[Byte](2048)(1))))
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.cipherOut.request(1)
      harness.cipherOut.expectNext(ApplicationCiphertext)
      harness.engine.overflowWraps shouldBe 1
      harness.engine.applicationWraps shouldBe 2
      harness.engine.largestOutputCapacity shouldBe poolBufferSize

      harness.cipherIn.sendComplete()
      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }
  }
}

class TlsGraphStageHeapBufferSpec
    extends TlsGraphStageBufferTestBase(
      ConfigFactory.parseString("pekko.io.tcp.direct-buffer-size = 4096")
        .withFallback(TlsGraphStageDeferredCloseSpec.config)) {

  "TlsGraphStage heap buffers" must {
    "share one allocation between the three initial transport buffers" in {
      val harness = newHarness(negotiatedPacketSize = 32768)
      finishInitialHandshake(harness)
      harness.plainIn.sendNext(SendBytes(ByteString.fromArray(Array.fill[Byte](2048)(1))))
      harness.cipherOut.request(1)
      harness.cipherOut.expectNext(ApplicationCiphertext)

      harness.engine.inputArray.length shouldBe 8192 * 3
      (harness.engine.inputArray eq harness.engine.outputArray) shouldBe true
      harness.cipherIn.sendComplete()
      harness.cipherOut.expectComplete()
      harness.plainOut.expectComplete()
      harness.plainIn.expectCancellation()
    }
  }
}

class TlsGraphStageBufferGrowthSpec
    extends TlsGraphStageBufferTestBase(
      ConfigFactory.parseString("pekko.io.tcp.direct-buffer-size = 18432")
        .withFallback(TlsGraphStageDeferredCloseSpec.config)) {

  "TlsGraphStage buffer growth" must {
    "avoid querying session sizes for an ordinary partial record" in {
      val harness = newHarness(negotiatedPacketSize = 32768)
      finishInitialHandshake(harness)
      val sessionQueries = harness.engine.sessionQueries
      harness.cipherIn.sendNext(TlsRecord)
      harness.cipherIn.expectRequest()
      awaitAssert(harness.engine.underflowUnwraps shouldBe 1)
      harness.plainOut.cancel()
      harness.cipherOut.cancel()
      harness.cipherIn.expectCancellation()
      harness.plainIn.expectCancellation()
      // Cancellation is processed after the stage handles the BUFFER_UNDERFLOW result.
      harness.engine.sessionQueries shouldBe sessionQueries
    }

    for (applicationSize <- Seq(0, 32700)) {
      s"grow inbound buffers after session sizes increase (application size $applicationSize)" in {
        val packetSize = 32768
        val harness = newHarness(negotiatedPacketSize = packetSize, negotiatedApplicationSize = applicationSize)
        finishInitialHandshake(harness)
        val bodySize = packetSize - 5
        val record = ByteString(23.toByte, 3.toByte, 3.toByte, (bodySize >> 8).toByte, bodySize.toByte) ++
          ByteString.fromArray(Array.fill[Byte](bodySize)(1))

        harness.cipherIn.sendNext(record)
        harness.plainOut.expectNext().asInstanceOf[SessionBytes].bytes shouldBe
        ByteString.fromArray(Array.fill[Byte](math.max(1, applicationSize))(0x41.toByte))
        harness.engine.unwrapCount shouldBe 2
        if (applicationSize == 0) harness.engine.underflowUnwraps should be > 0
        else harness.engine.overflowUnwraps should be > 0

        harness.plainIn.sendNext(SendBytes(ByteString.fromArray(Array.fill[Byte](65536)(1))))
        harness.cipherOut.request(1)
        harness.cipherOut.expectNext(ApplicationCiphertext)
        harness.engine.largestOutputCapacity should be >= packetSize

        // Successful records reset the retry guard; the connection can process more than eight records.
        for (_ <- 1 to 10) {
          harness.plainOut.request(1)
          harness.cipherIn.sendNext(record)
          harness.plainOut.expectNext().asInstanceOf[SessionBytes].bytes.size shouldBe math.max(1, applicationSize)
        }
        harness.plainOut.request(1)
        harness.cipherIn.sendComplete()
        harness.cipherOut.expectComplete()
        harness.plainOut.expectComplete()
        harness.plainIn.expectCancellation()
      }
    }

  }
}

class DeferredZeroTimerScheduler(config: Config, log: LoggingAdapter, threadFactory: ThreadFactory)
    extends ExplicitlyTriggeredScheduler(config, log, threadFactory) {

  override def scheduleOnce(delay: FiniteDuration, runnable: Runnable)(implicit
      executor: ExecutionContext): Cancellable =
    super.scheduleOnce(if (delay <= Duration.Zero) 1.millis else delay, runnable)
}

object TlsGraphStageDeferredCloseSpec {
  val config: Config =
    ConfigFactory
      .parseString(
        s"""pekko.scheduler.implementation = "${classOf[DeferredZeroTimerScheduler].getName}"
           |pekko.io.tcp.direct-buffer-pool-limit = 8
           |""".stripMargin)
      .withFallback(ConfigFactory.parseString(TlsSpec.configOverrides))
}
