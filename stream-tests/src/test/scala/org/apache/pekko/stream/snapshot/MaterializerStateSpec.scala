/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.snapshot

import javax.net.ssl.{ SSLEngine, SSLSession }

import scala.concurrent.Promise
import scala.util.Try

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.stream._
import pekko.stream.TLSProtocol.{ SendBytes, SessionBytes, SslTlsInbound, SslTlsOutbound }
import pekko.stream.io.TlsSpec
import pekko.stream.scaladsl.{ Flow, GraphDSL, Keep, Merge, Partition, Sink, Source, TLS }
import pekko.stream.testkit.scaladsl.TestSink
import pekko.testkit.{ PekkoSpec, TestKit }
import pekko.util.ByteString

class MaterializerStateSpec extends PekkoSpec() {
  private val previousLegacyActorPath = TlsSpec.setLegacyActorPath(true)

  override protected def afterTermination(): Unit = {
    TlsSpec.restoreLegacyActorPath(previousLegacyActorPath)
    super.afterTermination()
  }

  private def localTlsFlow(protocol: String): Flow[SslTlsOutbound, SslTlsInbound, NotUsed] = {
    val sslContext = TlsSpec.initSslContext(protocol)

    val ciphers =
      if (protocol == "TLSv1.3") TlsSpec.TLS13Ciphers.toArray
      else TlsSpec.TLS12Ciphers.toArray

    def createSSLEngine(role: TLSRole): SSLEngine = {
      val engine = sslContext.createSSLEngine()
      engine.setUseClientMode(role == Client)
      engine.setEnabledCipherSuites(ciphers)
      engine.setEnabledProtocols(Array(protocol))
      engine
    }

    def passthroughTls =
      Flow[SslTlsInbound].collect { case SessionBytes(_, bytes) => SendBytes(bytes) }

    TLS(() => createSSLEngine(Client), verifySession = (_: SSLSession) => Try(()), IgnoreComplete)
      .atop(TLS(() => createSSLEngine(Server), verifySession = (_: SSLSession) => Try(()), IgnoreComplete).reversed)
      .join(passthroughTls)
  }

  private def startRunningTlsStream()(implicit mat: Materializer): Unit =
    Source
      .single[SslTlsOutbound](SendBytes(ByteString("ping")))
      .concat(Source.maybe[SslTlsOutbound])
      .via(localTlsFlow("TLSv1.2"))
      .runWith(Sink.ignore)

  "The MaterializerSnapshotting" must {

    "snapshot a running stream" in {
      implicit val mat = Materializer(system)
      try {
        Source.maybe[Int].map(_.toString).zipWithIndex.runWith(Sink.seq)

        awaitAssert({
            val snapshot = MaterializerState.streamSnapshots(mat).futureValue

            snapshot should have size 1
            snapshot.head.activeInterpreters should have size 1
            snapshot.head.activeInterpreters.head.logics should have size 4 // all 4 operators
          }, remainingOrDefault)
      } finally {
        mat.shutdown()
      }
    }

    "snapshot a running stream on the default dispatcher" in {
      val promise = Promise[Int]()
      Source.future(promise.future).map(_.toString).zipWithIndex.runWith(Sink.seq)

      awaitAssert({
          val snapshot = MaterializerState.streamSnapshots(system).futureValue

          snapshot should have size 1
          snapshot.head.activeInterpreters should have size 1
          snapshot.head.activeInterpreters.head.logics should have size 4 // all 4 operators
        }, remainingOrDefault)
      promise.success(1)
    }

    "snapshot a running stream that includes the legacy TLS path" in {
      implicit val mat = Materializer(system)
      try {
        startRunningTlsStream()

        awaitAssert({
            val snapshots = MaterializerState.streamSnapshots(mat).futureValue
            snapshots should have size 3
            snapshots.count(_.toString.contains("TLS-for-flow-")) should be(2)
          }, remainingOrDefault)
      } finally {
        mat.shutdown()
      }
    }

    "snapshot a running stream that includes the async GraphStage TLS path" in {
      val previousGraphStageSetting = TlsSpec.setLegacyActorPath(false)
      val tlsSystem = ActorSystem("MaterializerStateGraphStageTlsSpec", PekkoSpec.testConf)

      try {
        implicit val mat = Materializer(tlsSystem)

        startRunningTlsStream()

        awaitAssert({
            val snapshots = MaterializerState.streamSnapshots(mat).futureValue
            snapshots should have size 3
            snapshots.count(_.toString.contains("TlsGraphStage")) should be(2)
            (snapshots.toString should not).include("TLS-for-flow-")
          }, remainingOrDefault)
      } finally {
        TestKit.shutdownActorSystem(tlsSystem)
        TlsSpec.restoreLegacyActorPath(previousGraphStageSetting)
      }
    }

    "snapshot a stream that has a stopped stage" in {
      implicit val mat = Materializer(system)
      try {
        val probe = TestSink[String]()
        val out = Source
          .single("one")
          .concat(Source.maybe[String]) // make sure we leave it running
          .runWith(probe)
        out.requestNext("one")
        awaitAssert({
            val snapshot = MaterializerState.streamSnapshots(mat).futureValue
            snapshot should have size 1
            snapshot.head.activeInterpreters should have size 1
            snapshot.head.activeInterpreters.head.stoppedLogics should have size 2 // Source.single and a detach
          }, remainingOrDefault)

      } finally {
        mat.shutdown()
      }
    }

    "snapshot a more complicated graph" in {
      implicit val mat = Materializer(system)
      try {
        // snapshot before anything is running
        MaterializerState.streamSnapshots(mat).futureValue

        val graph = Flow.fromGraph(GraphDSL.create() { implicit b =>
          import GraphDSL.Implicits._
          val partition = b.add(Partition[String](4,
            {
              case "green" => 0
              case "red"   => 1
              case "blue"  => 2
              case _       => 3
            }))
          val merge = b.add(Merge[String](4, eagerComplete = false))
          val discard = b.add(Sink.ignore.async)
          val one = b.add(Source.single("purple"))

          partition.out(0)                                              ~> merge.in(0)
          partition.out(1).via(Flow[String].map(_.toUpperCase()).async) ~> merge.in(1)
          partition.out(2).groupBy(2, identity).mergeSubstreams         ~> merge.in(2)
          partition.out(3)                                              ~> discard

          one ~> merge.in(3)

          FlowShape(partition.in, merge.out)
        })

        val callMeMaybe =
          Source.maybe[String].viaMat(graph)(Keep.left).toMat(Sink.ignore)(Keep.left).run()

        // just check that we can snapshot without errors
        MaterializerState.streamSnapshots(mat).futureValue
        callMeMaybe.success(Some("green"))
        MaterializerState.streamSnapshots(mat).futureValue
        Thread.sleep(100) // just to give it a bigger chance to cover different states of shutting down
        MaterializerState.streamSnapshots(mat).futureValue

      } finally {
        mat.shutdown()
      }
    }

  }

}
