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

import javax.net.ssl.SSLContext

import scala.concurrent.duration._
import scala.util.Success

import org.apache.pekko
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.io.TlsGraphStage
import pekko.stream.scaladsl._
import pekko.stream.stage._
import pekko.stream.testkit.StreamSpec
import pekko.stream.testkit.scaladsl.{ TestSink, TestSource }
import pekko.testkit.WithLogCapturing
import pekko.util.ByteString

class TlsGraphStageCancellationSpec extends StreamSpec(TlsSpec.configOverrides) with WithLogCapturing {

  private def tls(context: SSLContext, client: Boolean) =
    BidiFlow
      .fromGraph(new TlsGraphStage(
        () => {
          val engine = context.createSSLEngine()
          engine.setUseClientMode(client)
          engine.setEnabledProtocols(Array(context.getProtocol))
          engine
        },
        _ => Success(()),
        IgnoreComplete))
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
}
