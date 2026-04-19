/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory }

import scala.concurrent.Await
import scala.concurrent.duration._

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.Done
import pekko.actor.ActorSystem
import pekko.stream.scaladsl.{ Flow, Keep, RunnableGraph, Sink, Source, TLS }
import pekko.stream.TLSProtocol.{ SendBytes, SessionBytes, SslTlsInbound, SslTlsOutbound }
import pekko.stream.impl.io.TlsGraphStage
import pekko.util.ByteString

object TlsBenchmark {
  final val OperationsPerInvocation = 256
  private final val Password = "changeme".toCharArray
  private final val Protocol = "TLSv1.2"
  private final val Payload = ByteString("abcdefgh" * 32)
  private final val Ciphers = Array(
    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")

  private def initSslContext(): SSLContext = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(getClass.getResourceAsStream("/keystore"), Password)

    val trustStore = KeyStore.getInstance(KeyStore.getDefaultType)
    trustStore.load(getClass.getResourceAsStream("/truststore"), Password)

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(keyStore, Password)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(trustStore)

    val context = SSLContext.getInstance(Protocol)
    context.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, new SecureRandom)
    context
  }

  private def createSSLEngine(context: SSLContext, role: TLSRole): SSLEngine = {
    val engine = context.createSSLEngine()
    engine.setUseClientMode(role == Client)
    engine.setEnabledProtocols(Array(Protocol))
    engine.setEnabledCipherSuites(Ciphers)
    engine
  }

  private def withLegacyActorPath[T](enabled: Boolean)(thunk: => T): T = {
    val previous = Option(System.getProperty(TlsGraphStage.UseLegacyActorPath))
    System.setProperty(TlsGraphStage.UseLegacyActorPath, if (enabled) "on" else "off")
    try thunk
    finally
      previous match {
        case Some(value) => System.setProperty(TlsGraphStage.UseLegacyActorPath, value)
        case None        => System.clearProperty(TlsGraphStage.UseLegacyActorPath)
      }
  }
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
class TlsBenchmark {
  import TlsBenchmark._

  private val legacySystem = withLegacyActorPath(true)(ActorSystem("TlsLegacyBenchmark"))
  private val graphStageSystem = withLegacyActorPath(false)(ActorSystem("TlsGraphStageBenchmark"))

  private val sslContext = initSslContext()

  private implicit val legacyMaterializer: Materializer = Materializer(legacySystem)
  private implicit val graphStageMaterializer: Materializer = Materializer(graphStageSystem)

  private def echoTlsFlow(closing: TLSClosing) =
    TLS(() => createSSLEngine(sslContext, Client), closing)
      .atop(TLS(() => createSSLEngine(sslContext, Server), closing).reversed)
      .join(Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => SendBytes(bytes) })

  private val legacyRoundTrip: RunnableGraph[scala.concurrent.Future[Done]] =
    withLegacyActorPath(true) {
      Source
        .repeat[SslTlsOutbound](SendBytes(Payload))
        .take(OperationsPerInvocation.toLong)
        .via(echoTlsFlow(EagerClose))
        .toMat(Sink.ignore)(Keep.right)
    }

  private val graphStageRoundTrip: RunnableGraph[scala.concurrent.Future[Done]] =
    withLegacyActorPath(false) {
      Source
        .repeat[SslTlsOutbound](SendBytes(Payload))
        .take(OperationsPerInvocation.toLong)
        .via(echoTlsFlow(EagerClose))
        .toMat(Sink.ignore)(Keep.right)
    }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def legacyTlsActorPath(): Unit =
    Await.result(legacyRoundTrip.run()(legacyMaterializer), 30.seconds)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def graphStageTlsPath(): Unit =
    Await.result(graphStageRoundTrip.run()(graphStageMaterializer), 30.seconds)

  @TearDown
  def shutdown(): Unit = {
    legacyMaterializer.shutdown()
    graphStageMaterializer.shutdown()
    Await.result(legacySystem.terminate(), 5.seconds)
    Await.result(graphStageSystem.terminate(), 5.seconds)
  }
}
