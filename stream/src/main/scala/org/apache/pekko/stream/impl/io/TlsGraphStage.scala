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

package org.apache.pekko.stream.impl.io

import java.nio.ByteBuffer
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import javax.net.ssl.SSLEngineResult.Status._
import javax.net.ssl._

import scala.annotation.{ switch, tailrec }
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

import org.apache.pekko

import pekko.annotation.InternalApi
import pekko.io.{ BufferPool, Tcp }
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, InHandler, OutHandler, TimerGraphStageLogic }
import pekko.util.ByteString

/**
 * INTERNAL API.
 *
 * GraphStage adapter for Pekko's existing TLS pump state machine. The phases
 * intentionally mirror [[TLSActor]] so the new implementation keeps the legacy
 * close, renegotiation, and SSLEngine workaround semantics while avoiding the
 * actor/FanoutProcessor substrate.
 */
@InternalApi private[stream] final class TlsGraphStage(
    createSSLEngine: () => SSLEngine,
    verifySession: SSLSession => Try[Unit],
    closing: TLSClosing)
    extends GraphStage[BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound]] {

  private val plainIn = Inlet[SslTlsOutbound]("TlsGraphStage.plainIn")
  private val plainOut = Outlet[SslTlsInbound]("TlsGraphStage.plainOut")
  private val cipherIn = Inlet[ByteString]("TlsGraphStage.cipherIn")
  private val cipherOut = Outlet[ByteString]("TlsGraphStage.cipherOut")

  override val shape: BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound] =
    BidiShape(plainIn, cipherOut, cipherIn, plainOut)

  override def initialAttributes: Attributes = DefaultAttributes.tlsGraphStage

  override def createLogic(inheritedAttributes: Attributes): TimerGraphStageLogic =
    new TimerGraphStageLogic(shape) { logic =>
      import TlsGraphStage._

      private var engine: SSLEngine = _
      private var currentSession: SSLSession = _
      private var lastHandshakeStatus: HandshakeStatus = NOT_HANDSHAKING
      private var inlineHandshakeReentries = 0
      private var phase: Byte = BidirectionalPhase.toByte
      private var deferredTransportFlushes = 0
      private var stateBits = 0

      private var transportOutBuffer: ByteBuffer = _
      private var userOutBuffer: ByteBuffer = _
      private var transportInBuffer: ByteBuffer = _
      private var transportUnreadBuffer: ByteBuffer = _
      private var userInBuffer: ByteBuffer = _
      private var bufferPool: BufferPool = _
      private var pooledBufferSize = 0
      private var transportPacketSize = 0
      private var applicationRecordSize = 0
      private var maxUserInBufferSize = 0
      private var maxUserOutBufferSize = 0

      private val userInput = new UserInputSlot(plainIn)
      private val transportInput = new TransportInputSlot(cipherIn)
      private val userOutput =
        new OutputSlot[SslTlsInbound](plainOut, UserOutputCancelledFlag, UserOutputCompletedFlag, UserOutputErroredFlag)
      private val transportOutput =
        new OutputSlot[ByteString](
          cipherOut,
          TransportOutputCancelledFlag,
          TransportOutputCompletedFlag,
          TransportOutputErroredFlag,
          deferInitialPumpOutput = true)

      setHandler(plainIn, userInput)
      setHandler(cipherIn, transportInput)
      setHandler(cipherOut, transportOutput)
      setHandler(plainOut, userOutput)

      override def preStart(): Unit =
        try {
          val tcp = Tcp(materializer.system)
          bufferPool = tcp.bufferPool
          pooledBufferSize = tcp.Settings.DirectBufferSize

          engine = createSSLEngine()
          currentSession = engine.getSession
          allocateBuffers(currentSession)
          engine.beginHandshake()
          lastHandshakeStatus = engine.getHandshakeStatus
          nextPhase(BidirectionalPhase)
          pullInputs()
        } catch {
          case NonFatal(ex) => failTls(ex)
        }

      override protected def onTimer(timerKey: Any): Unit =
        if (timerKey == InitialOutputFlushTimer && (stateBits & StageClosedFlag) == 0) {
          transportOutput.pushPending()
          runPump()
        } else if (timerKey == TransportFlushTimer && (stateBits & TransportFlushPendingFlag) != 0) {
          stateBits &= ~TransportFlushPendingFlag
          flushToTransport()
          runPump()
        } else if (timerKey == UserInputBatchTimer && (stateBits & UserInputBatchPendingFlag) != 0) {
          stateBits &= ~UserInputBatchPendingFlag
          runPump()
        }

      private def allocateBuffers(session: SSLSession): Unit = {
        val packetSize = math.max(1, session.getPacketBufferSize)
        val applicationSize = math.max(1, session.getApplicationBufferSize)
        val applicationBatchSize = applicationBufferSize(applicationSize, packetSize)
        transportPacketSize = packetSize
        applicationRecordSize = applicationSize
        maxUserInBufferSize = applicationBatchSize
        maxUserOutBufferSize = applicationBatchSize

        transportOutBuffer = acquireTransportBuffer(packetSize)
        transportInBuffer = acquireTransportBuffer(packetSize)
        transportUnreadBuffer = acquireTransportBuffer(packetSize)
        userInBuffer = ByteBuffer.allocate(applicationSize)
        userOutBuffer = ByteBuffer.allocate(applicationSize)

        TlsEngineHelpers.emptyReadBuffer(userInBuffer)
        TlsEngineHelpers.emptyReadBuffer(transportInBuffer)
        TlsEngineHelpers.emptyReadBuffer(transportUnreadBuffer)
      }

      private def applicationBufferSize(applicationSize: Int, packetSize: Int): Int = {
        val recordsThatFitTransportBuffer = math.max(1, pooledBufferSize / packetSize)
        val recordsPerEngineCall = math.min(MaxApplicationRecordsPerEngineCall, recordsThatFitTransportBuffer)
        applicationSize * recordsPerEngineCall
      }

      private def runPump(): Unit =
        if ((stateBits & (StageClosedFlag | StageCompletionPendingFlag)) == 0 &&
          (stateBits & InitialPumpEnabledFlag) != 0) {
          pump()
          if ((stateBits & (StageClosedFlag | StageCompletionPendingFlag)) == 0) {
            pullInputs()
            schedulePendingTransportFlush()
          }
        }

      private def pump(): Unit = {
        try {
          while (phaseExecutable)
            runPhase()
        } catch {
          case NonFatal(ex) => failTls(ex)
        }

        if (phaseCompleted)
          pumpFinished()
      }

      private def nextPhase(next: Int): Unit =
        phase = next.toByte

      private def phaseExecutable: Boolean =
        (phase.toInt: @switch) match {
          case BidirectionalPhase    => bidirectionalReady && !bidirectionalCompleted
          case FlushingOutboundPhase => outboundHalfClosedReady && !outboundHalfClosedCompleted
          case AwaitingClosePhase    => awaitingCloseReady && !awaitingCloseCompleted
          case OutboundClosedPhase   => (outboundHalfClosedReady || inboundReady) && !outboundClosedCompleted
          case InboundClosedPhase    => (outboundReady || inboundHalfClosedReady) && !inboundClosedCompleted
          case _                     => false
        }

      private def phaseCompleted: Boolean =
        (phase.toInt: @switch) match {
          case BidirectionalPhase    => bidirectionalCompleted
          case FlushingOutboundPhase => outboundHalfClosedCompleted
          case AwaitingClosePhase    => awaitingCloseCompleted
          case OutboundClosedPhase   => outboundClosedCompleted
          case InboundClosedPhase    => inboundClosedCompleted
          case CompletedPhase        => true
          case _                     => false
        }

      private def runPhase(): Unit =
        (phase.toInt: @switch) match {
          case BidirectionalPhase =>
            val continue = doInbound(isOutboundClosed = false, inboundHalfClosedMode = false)
            if (continue) doOutbound(isInboundClosed = false)

          case FlushingOutboundPhase =>
            try doWrap()
            catch { case _: SSLException => nextPhase(CompletedPhase) }

          case AwaitingClosePhase =>
            transportInput.chopInto(transportInBuffer)
            if (TlsEngineHelpers.hasCompleteTlsRecord(transportInBuffer))
              try doUnwrap(ignoreOutput = true)
              catch { case _: SSLException => nextPhase(CompletedPhase) }

          case OutboundClosedPhase =>
            val continue = doInbound(isOutboundClosed = true, inboundHalfClosedMode = false)
            if (continue && outboundHalfClosedReady) {
              try doWrap()
              catch { case _: SSLException => nextPhase(CompletedPhase) }
            }

          case InboundClosedPhase =>
            val continue = doInbound(isOutboundClosed = false, inboundHalfClosedMode = true)
            if (continue) doOutbound(isInboundClosed = true)

          case _ =>
        }

      private def userHasDataReady: Boolean = (stateBits & PlainDataAllowedFlag) != 0 &&
        (userInBufferHasRemaining || userInput.isReady) &&
        lastHandshakeStatus != NEED_UNWRAP

      private def userHasDataCompleted: Boolean =
        userInput.isCancelled || userInput.isDepleted

      private def engineNeedsWrapReady: Boolean =
        lastHandshakeStatus == NEED_WRAP

      private def engineNeedsWrapCompleted: Boolean =
        engine.isOutboundDone

      private def userOrEngineOutboundReady: Boolean =
        userHasDataReady || engineNeedsWrapReady

      private def userOrEngineOutboundCompleted: Boolean =
        userHasDataCompleted && engineNeedsWrapCompleted

      private def bidirectionalReady: Boolean =
        outboundReady || inboundReady

      private def bidirectionalCompleted: Boolean =
        outboundCompleted && inboundCompleted

      private def outboundReady: Boolean =
        userOrEngineOutboundReady && transportOutput.isDemandAvailable

      private def outboundCompleted: Boolean =
        userOrEngineOutboundCompleted || transportOutput.isClosed

      private def inboundReady: Boolean = (transportInput.isReady && userOutput.isDemandAvailable) ||
        userOutput.isCancelled

      private def inboundCompleted: Boolean = (transportInput.isCompleted || userOutput.isClosed) &&
        (engine.isInboundDone || userOutput.isErrored)

      private def outboundHalfClosedReady: Boolean =
        engineNeedsWrapReady && transportOutput.isDemandAvailable

      private def outboundHalfClosedCompleted: Boolean =
        engineNeedsWrapCompleted || transportOutput.isClosed

      private def inboundHalfClosedReady: Boolean =
        transportInput.isReady && !engine.isInboundDone

      private def inboundHalfClosedCompleted: Boolean =
        transportInput.isCompleted || engine.isInboundDone

      private def outboundClosedCompleted: Boolean =
        outboundHalfClosedCompleted && inboundCompleted

      private def inboundClosedCompleted: Boolean =
        outboundCompleted && inboundHalfClosedCompleted

      private def awaitingCloseReady: Boolean =
        transportInput.isPending && !engine.isInboundDone

      private def awaitingCloseCompleted: Boolean =
        transportInput.isDepleted || transportInput.isCancelled || engine.isInboundDone

      private def pullInputs(): Unit = {
        userInput.pullIfNeeded(!userInBufferHasRemaining)
        transportInput.pullIfNeeded(transportInput.isEmpty)
      }

      private def completeOrFlush(): Unit = {
        if (transportOutBuffer.position() > 0)
          flushToTransport()

        if (engine.isOutboundDone || (engine.isInboundDone && !engineNeedsWrapReady))
          nextPhase(CompletedPhase)
        else nextPhase(FlushingOutboundPhase)
      }

      private def doInbound(isOutboundClosed: Boolean, inboundHalfClosedMode: Boolean): Boolean =
        if (transportInput.isDepleted && transportInput.isEmpty) {
          try engine.closeInbound()
          catch {
            case _: SSLException => userOutput.enqueue(SessionTruncated)
          }
          lastHandshakeStatus = engine.getHandshakeStatus
          completeOrFlush()
          false
        } else if (!inboundHalfClosedMode && userOutput.isCancelled) {
          if (!isOutboundClosed && closing.ignoreCancel) {
            nextPhase(InboundClosedPhase)
          } else {
            engine.closeOutbound()
            lastHandshakeStatus = engine.getHandshakeStatus
            nextPhase(FlushingOutboundPhase)
          }
          true
        } else if (if (inboundHalfClosedMode) inboundHalfClosedReady else inboundReady) {
          transportInput.chopInto(transportInBuffer)
          if (TlsEngineHelpers.hasCompleteTlsRecord(transportInBuffer))
            try {
              doUnwrap(ignoreOutput = inboundHalfClosedMode || userOutput.isCancelled)
              true
            } catch {
              case ex: SSLException =>
                failTls(ex, closeTransport = false)
                refreshHandshakeStatus()
                try engine.closeInbound()
                catch { case _: SSLException => () }
                completeOrFlush()
                false
            }
          else true
        } else true

      private def doOutbound(isInboundClosed: Boolean): Unit =
        if (userInput.isDepleted && userInput.isEmpty && !userInBufferHasRemaining && mayCloseOutbound) {
          if (transportOutBuffer.position() > 0)
            flushToTransport()
          if (!isInboundClosed && closing.ignoreComplete) {
            // keep the outbound TLS side open
          } else {
            engine.closeOutbound()
            lastHandshakeStatus = engine.getHandshakeStatus
          }
          nextPhase(OutboundClosedPhase)
        } else if (transportOutput.isCancelled) {
          nextPhase(CompletedPhase)
        } else if (outboundReady) {
          if (userHasDataReady && !userInBufferHasRemaining)
            userInput.chopInto(userInBuffer)
          try doWrap()
          catch {
            case ex: SSLException =>
              failTls(ex, closeTransport = false)
              refreshHandshakeStatus()
              completeOrFlush()
          }
        }

      private def mayCloseOutbound: Boolean =
        lastHandshakeStatus match {
          case HandshakeStatus.NOT_HANDSHAKING | HandshakeStatus.FINISHED => true
          case _                                                          => false
        }

      private def doWrap(): Unit = {
        val result = wrapIntoTransportBuffer()
        lastHandshakeStatus = result.getHandshakeStatus

        if (lastHandshakeStatus == FINISHED) handshakeFinished()
        runDelegatedTasks()

        result.getStatus match {
          case OK =>
            if (transportOutBuffer.position() == 0 && lastHandshakeStatus == NEED_WRAP)
              throw new IllegalStateException("SSLEngine trying to loop NEED_WRAP without producing output")

            flushToTransportIfNeeded(result)

          case CLOSED =>
            flushToTransport()
            if (engine.isInboundDone) nextPhase(CompletedPhase)
            else nextPhase(AwaitingClosePhase)

          case BUFFER_OVERFLOW =>
            growTransportOutBuffer()

          case status =>
            failTls(new IllegalStateException(s"unexpected status $status in doWrap()"))
        }
      }

      private def wrapIntoTransportBuffer(): SSLEngineResult = {
        var result = engine.wrap(userInBuffer, transportOutBuffer)

        while (canContinueWrapping(result)) {
          val userPosition = userInBuffer.position()
          val transportPosition = transportOutBuffer.position()
          result = engine.wrap(userInBuffer, transportOutBuffer)
          if (userInBuffer.position() == userPosition && transportOutBuffer.position() == transportPosition)
            return result
        }

        result
      }

      private def canContinueWrapping(result: SSLEngineResult): Boolean =
        result.getStatus == OK &&
        result.getHandshakeStatus == NOT_HANDSHAKING &&
        userInBuffer.hasRemaining &&
        transportOutBuffer.remaining >= transportPacketSize

      @tailrec
      private def doUnwrap(ignoreOutput: Boolean): Unit = {
        if (!ignoreOutput && transportInBuffer.remaining > transportPacketSize &&
          userOutBuffer.capacity < maxUserOutBufferSize)
          growUserOutBuffer()

        val oldInPosition = transportInBuffer.position()
        val result = engine.unwrap(transportInBuffer, userOutBuffer)
        if (ignoreOutput) userOutBuffer.clear()
        lastHandshakeStatus = result.getHandshakeStatus
        runDelegatedTasks()

        result.getStatus match {
          case OK =>
            result.getHandshakeStatus match {
              case NEED_WRAP =>
                inlineHandshakeReentries += 1
                if (inlineHandshakeReentries > MaxInlineEngineProgressAttempts)
                  throw new IllegalStateException(
                    s"Stuck in unwrap loop, bailing out, last handshake status [$lastHandshakeStatus], " +
                    s"remaining=${transportInBuffer.remaining}, out=${userOutBuffer.position()}, " +
                    "(https://github.com/apache/pekko/issues/442)")

                transportInput.putBackUnreadBuffer(transportInBuffer)

              case FINISHED =>
                flushToUser()
                handshakeFinished()
                transportInput.putBackUnreadBuffer(transportInBuffer)

              case NEED_UNWRAP
                  if transportInBuffer.hasRemaining &&
                  userOutBuffer.position() == 0 &&
                  transportInBuffer.position() == oldInPosition =>
                throw new IllegalStateException("SSLEngine trying to loop NEED_UNWRAP without producing output")

              case _ =>
                if (transportInBuffer.hasRemaining) doUnwrap(ignoreOutput)
                else flushToUser()
            }

          case CLOSED =>
            flushToUser()
            completeOrFlush()

          case BUFFER_UNDERFLOW =>
            flushToUser()

          case BUFFER_OVERFLOW =>
            flushToUser()
            transportInput.putBackUnreadBuffer(transportInBuffer)

          case null =>
            failTls(new IllegalStateException("unexpected status null in doUnwrap()"))
        }
      }

      private def runDelegatedTasks(): Unit =
        if (TlsEngineHelpers.runDelegatedTasks(engine) > 0 || lastHandshakeStatus == NEED_TASK)
          lastHandshakeStatus = engine.getHandshakeStatus

      private def handshakeFinished(): Unit = {
        val session = engine.getSession
        verifySession(session) match {
          case Success(()) =>
            currentSession = session
            stateBits |= PlainDataAllowedFlag
            flushToUser()

          case Failure(ex) =>
            throw ex
        }
      }

      private def setNewSessionParameters(params: NegotiateNewSession): Unit = {
        if (transportOutBuffer.position() > 0)
          flushToTransport()
        currentSession.invalidate()
        TlsUtils.applySessionParameters(engine, params)
        engine.beginHandshake()
        lastHandshakeStatus = engine.getHandshakeStatus
        stateBits &= ~PlainDataAllowedFlag
      }

      private def flushToTransport(): Unit = {
        if ((stateBits & TransportFlushPendingFlag) != 0) {
          stateBits &= ~TransportFlushPendingFlag
          cancelTimer(TransportFlushTimer)
        }
        deferredTransportFlushes = 0
        transportOutBuffer.flip()
        if (transportOutBuffer.hasRemaining && !transportOutput.isClosed)
          transportOutput.enqueue(ByteString(transportOutBuffer))
        transportOutBuffer.clear()
      }

      private def flushToTransportIfNeeded(result: SSLEngineResult): Unit =
        if (transportOutBuffer.position() == 0) {
          if ((stateBits & TransportFlushPendingFlag) != 0) {
            stateBits &= ~TransportFlushPendingFlag
            cancelTimer(TransportFlushTimer)
          }
          deferredTransportFlushes = 0
        } else if (shouldFlushTransportNow(result)) {
          flushToTransport()
        } else {
          deferredTransportFlushes += 1
          if (deferredTransportFlushes >= DeferredTransportFlushLimit) {
            flushToTransport()
          } else {
            stateBits |= TransportFlushPendingFlag
          }
        }

      private def schedulePendingTransportFlush(): Unit =
        if ((stateBits & TransportFlushPendingFlag) != 0) {
          if (transportOutput.isClosed) {
            stateBits &= ~TransportFlushPendingFlag
            deferredTransportFlushes = 0
            transportOutBuffer.clear()
          } else if (!isTimerActive(TransportFlushTimer)) {
            scheduleOnce(TransportFlushTimer, Duration.Zero)
          }
        }

      private def shouldFlushTransportNow(result: SSLEngineResult): Boolean =
        if (result.getHandshakeStatus != NOT_HANDSHAKING ||
          engine.isOutboundDone ||
          (stateBits & PlainDataAllowedFlag) == 0 ||
          transportOutput.isCancelled) {
          true
        } else if (transportOutBuffer.position() >= transportPacketSize) {
          true
        } else shouldFlushApplicationDataNow(result.bytesConsumed())

      private def shouldFlushApplicationDataNow(bytesConsumed: Int): Boolean =
        bytesConsumed <= 0 || bytesConsumed > MaxBatchedApplicationBytes ||
        bytesConsumed >= applicationRecordSize

      private def flushToUser(): Unit = {
        if (inlineHandshakeReentries > 0) inlineHandshakeReentries = 0
        userOutBuffer.flip()
        if (userOutBuffer.hasRemaining)
          userOutput.enqueue(SessionBytes(currentSession, ByteString(userOutBuffer)))
        userOutBuffer.clear()
      }

      private def growTransportOutBuffer(): Unit = {
        val oldBuffer = transportOutBuffer
        val oldCapacity = oldBuffer.capacity()
        if (oldCapacity > Int.MaxValue / 2)
          throw new IllegalStateException(s"Cannot grow TLS transport output buffer beyond $oldCapacity bytes")

        val bigger = acquireTransportBuffer(oldCapacity * 2)
        transportOutBuffer.flip()
        bigger.put(transportOutBuffer)
        releaseTransportBuffer(oldBuffer)
        transportOutBuffer = bigger
      }

      private def failTls(ex: Throwable): Unit = failTls(ex, closeTransport = true)

      private def failTls(ex: Throwable, closeTransport: Boolean): Unit =
        if ((stateBits & StageClosedFlag) == 0) {
          userInput.cancel()
          transportInput.cancel()
          if (closeTransport) {
            stateBits |= StageClosedFlag
            failStage(ex)
          } else {
            writeFailureAlert()
            userOutput.failOutput(ex)
          }
        }

      private val EmptyApplicationBuffer = ByteBuffer.allocate(0)

      private def writeFailureAlert(): Unit =
        if ((engine ne null) && !transportOutput.isCancelled)
          try {
            if (!engine.isOutboundDone)
              engine.closeOutbound()
            val result = engine.wrap(EmptyApplicationBuffer, transportOutBuffer)
            lastHandshakeStatus = result.getHandshakeStatus
            flushToTransport()
            EmptyApplicationBuffer.clear()
          } catch {
            case NonFatal(_) => EmptyApplicationBuffer.clear()
          }

      private def refreshHandshakeStatus(): Unit =
        if (engine ne null)
          try {
            lastHandshakeStatus = engine.getHandshakeStatus
          } catch {
            case NonFatal(_) => ()
          }

      private def pumpFinished(): Unit =
        if ((stateBits & StageClosedFlag) == 0) {
          userInput.cancel()
          transportInput.cancel()
          transportOutput.completeOutput()
          userOutput.completeOutput()
          stateBits |= StageCompletionPendingFlag
          completeStageIfReady()
        }

      private def completeStageIfReady(): Unit =
        if ((stateBits & StageCompletionPendingFlag) != 0 &&
          (stateBits & StageClosedFlag) == 0 &&
          !userOutput.hasPending &&
          !transportOutput.hasPending) {
          stateBits |= StageClosedFlag
          completeStage()
        }

      override def postStop(): Unit = {
        releaseBuffers()
        super.postStop()
      }

      private def userInBufferHasRemaining: Boolean = (userInBuffer ne null) && userInBuffer.hasRemaining

      private def growUserInBuffer(availableBytes: Int): ByteBuffer = {
        val requested = math.min(maxUserInBufferSize, math.max(applicationRecordSize, availableBytes))
        val records = (requested + applicationRecordSize - 1) / applicationRecordSize
        val capacity = math.min(maxUserInBufferSize, records * applicationRecordSize)
        val expanded = ByteBuffer.allocate(capacity)
        userInBuffer = expanded
        expanded
      }

      private def growUserOutBuffer(): Unit = {
        val expanded = ByteBuffer.allocate(maxUserOutBufferSize)
        userOutBuffer.flip()
        expanded.put(userOutBuffer)
        userOutBuffer = expanded
      }

      private def releaseBuffers(): Unit = {
        releaseTransportBuffer(transportOutBuffer)
        transportOutBuffer = null
        userOutBuffer = null
        releaseTransportBuffer(transportInBuffer)
        transportInBuffer = null
        releaseTransportBuffer(transportUnreadBuffer)
        transportUnreadBuffer = null
        userInBuffer = null
      }

      private def acquireTransportBuffer(requiredCapacity: Int): ByteBuffer = {
        val buffer = bufferPool.acquire()
        if (buffer.capacity >= requiredCapacity) buffer
        else {
          releaseTransportBuffer(buffer)
          throw new IllegalStateException(
            s"TLS packet buffer requires $requiredCapacity bytes but pekko.io.tcp.direct-buffer-size is $pooledBufferSize")
        }
      }

      private def releaseTransportBuffer(buffer: ByteBuffer): Unit =
        if (buffer ne null) {
          buffer.clear()
          bufferPool.release(buffer)
        }

      private final class UserInputSlot(inlet: Inlet[SslTlsOutbound]) extends InHandler {
        private val pendingQueue = new Array[AnyRef](UserInputQueueSize)
        private var pendingHead = 0
        private var pendingCount = 0
        private var bytes: ByteString = ByteString.empty
        private var bytesOffset = 0
        private var bytesRemaining = 0

        def isReady: Boolean = bytesRemaining > 0 || pendingCount != 0 || isDepleted
        def isCompleted: Boolean = (stateBits & UserInputCancelledFlag) != 0
        def isEmpty: Boolean = bytesRemaining == 0 && pendingCount == 0
        def isPending: Boolean = pendingCount != 0
        def isDepleted: Boolean = (stateBits & UserInputFinishedFlag) != 0 && pendingCount == 0 && bytesRemaining == 0
        def isCancelled: Boolean = (stateBits & UserInputCancelledFlag) != 0

        override def onPush(): Unit = {
          stateBits |= InitialPumpEnabledFlag
          val elem = grab(inlet)
          enqueue(elem)
          pullIfNeeded(bytesDrained = true)

          if (shouldDefer(elem) && pendingCount < UserInputQueueSize) {
            stateBits |= UserInputBatchPendingFlag
            if (!isTimerActive(UserInputBatchTimer))
              scheduleOnce(UserInputBatchTimer, Duration.Zero)
          } else {
            cancelPendingBatchTimer()
            runPump()
          }
        }

        override def onUpstreamFinish(): Unit = {
          stateBits |= InitialPumpEnabledFlag
          stateBits |= UserInputFinishedFlag
          cancelPendingBatchTimer()
          runPump()
        }

        override def onUpstreamFailure(ex: Throwable): Unit =
          failTls(ex)

        private def shouldDefer(elem: SslTlsOutbound): Boolean =
          elem match {
            case SendBytes(bs) =>
              bs.size <= MaxBatchedApplicationBytes &&
              (stateBits & PlainDataAllowedFlag) != 0 &&
              (lastHandshakeStatus == NOT_HANDSHAKING || lastHandshakeStatus == FINISHED) &&
              !userInBufferHasRemaining &&
              !engine.isOutboundDone &&
              !transportOutput.isClosed

            case _ => false
          }

        private def cancelPendingBatchTimer(): Unit =
          if ((stateBits & UserInputBatchPendingFlag) != 0) {
            stateBits &= ~UserInputBatchPendingFlag
            if (isTimerActive(UserInputBatchTimer))
              cancelTimer(UserInputBatchTimer)
          }

        private def enqueue(elem: SslTlsOutbound): Unit = {
          if (pendingCount >= UserInputQueueSize)
            throw new IllegalStateException("TLS user input queue is full")

          pendingQueue((pendingHead + pendingCount) & UserInputQueueMask) = elem.asInstanceOf[AnyRef]
          pendingCount += 1
          stateBits |= UserInputHasPendingFlag
        }

        private def peek(): SslTlsOutbound =
          pendingQueue(pendingHead).asInstanceOf[SslTlsOutbound]

        private def dequeue(): SslTlsOutbound = {
          val elem = pendingQueue(pendingHead).asInstanceOf[SslTlsOutbound]
          pendingQueue(pendingHead) = null
          pendingHead = (pendingHead + 1) & UserInputQueueMask
          pendingCount -= 1
          if (pendingCount == 0) {
            pendingHead = 0
            stateBits &= ~UserInputHasPendingFlag
          }
          elem
        }

        def pullIfNeeded(bytesDrained: Boolean): Unit =
          if (bytesDrained &&
            bytesRemaining == 0 &&
            pendingCount < UserInputQueueSize &&
            (stateBits & UserInputFinishedFlag) == 0 &&
            (stateBits & UserInputCancelledFlag) == 0 &&
            !hasBeenPulled(inlet))
            pull(inlet)

        def cancel(): Unit =
          if ((stateBits & UserInputCancelledFlag) == 0) {
            stateBits |= UserInputCancelledFlag
            cancelPendingBatchTimer()
            clearPending()
            clearBytes()
            if ((stateBits & UserInputFinishedFlag) == 0 && !logic.isClosed(inlet)) logic.cancel(inlet)
            stateBits |= UserInputFinishedFlag
          }

        private def clearPending(): Unit = {
          var i = 0
          while (i < pendingCount) {
            pendingQueue((pendingHead + i) & UserInputQueueMask) = null
            i += 1
          }
          pendingHead = 0
          pendingCount = 0
          stateBits &= ~UserInputHasPendingFlag
        }

        private def setBytes(next: ByteString): Unit = {
          bytes = next
          bytesOffset = 0
          bytesRemaining = next.size
        }

        private def clearBytes(): Unit = {
          bytes = ByteString.empty
          bytesOffset = 0
          bytesRemaining = 0
        }

        def chopInto(buffer: ByteBuffer): Unit = {
          var target = buffer
          TlsEngineHelpers.prepareForAppend(target)

          var continue = true
          while (continue && target.hasRemaining && (bytesRemaining > 0 || pendingCount != 0)) {
            if (bytesRemaining == 0 && pendingCount != 0) {
              peek() match {
                case SendBytes(bs) =>
                  if (bs.isEmpty) dequeue()
                  else if (bs.size > target.remaining && target.position() != 0) continue = false
                  else {
                    if (bs.size > target.remaining && target.capacity < maxUserInBufferSize)
                      target = growUserInBuffer(bs.size)
                    dequeue()
                    setBytes(bs)
                  }

                case n: NegotiateNewSession =>
                  if (target.position() == 0) {
                    dequeue()
                    setNewSessionParameters(n)
                  }
                  continue = false

                case null =>
                  throw new IllegalArgumentException(s"Unexpected TLS input element: null")
              }
            }

            if (continue && bytesRemaining > 0) {
              if (bytesRemaining > target.remaining && target.position() == 0 && target.capacity < maxUserInBufferSize)
                target = growUserInBuffer(bytesRemaining)

              val copied = bytes.copyToBuffer(target, bytesOffset)
              if (copied == bytesRemaining) clearBytes()
              else if (copied > 0) {
                bytesOffset += copied
                bytesRemaining -= copied
                continue = false
              } else continue = false
            }
          }

          target.flip()
        }
      }

      private final class TransportInputSlot(inlet: Inlet[ByteString]) extends InHandler {
        private var pending: ByteString = _
        private var bytes: ByteString = ByteString.empty
        private var bytesOffset = 0
        private var bytesRemaining = 0

        private def unreadBufferHasRemaining: Boolean = (transportUnreadBuffer ne null) &&
          transportUnreadBuffer.hasRemaining

        def isReady: Boolean =
          unreadBufferHasRemaining || bytesRemaining > 0 || (stateBits & TransportInputHasPendingFlag) != 0 ||
          isDepleted
        def isCompleted: Boolean = (stateBits & TransportInputCancelledFlag) != 0
        def isEmpty: Boolean = bytesRemaining == 0 && !unreadBufferHasRemaining
        def isPending: Boolean =
          unreadBufferHasRemaining || bytesRemaining > 0 || (stateBits & TransportInputHasPendingFlag) != 0
        def isDepleted: Boolean = (stateBits & TransportInputFinishedFlag) != 0 &&
          (stateBits & TransportInputHasPendingFlag) == 0 &&
          bytesRemaining == 0 &&
          !unreadBufferHasRemaining
        def isCancelled: Boolean = (stateBits & TransportInputCancelledFlag) != 0

        override def onPush(): Unit = {
          stateBits |= InitialPumpEnabledFlag
          pending = grab(inlet)
          stateBits |= TransportInputHasPendingFlag
          runPump()
        }

        override def onUpstreamFinish(): Unit = {
          stateBits |= InitialPumpEnabledFlag
          stateBits |= TransportInputFinishedFlag
          runPump()
        }

        override def onUpstreamFailure(ex: Throwable): Unit =
          failTls(ex)

        private def dequeue(): ByteString = {
          val elem = pending
          pending = null
          stateBits &= ~TransportInputHasPendingFlag
          elem
        }

        def pullIfNeeded(bytesDrained: Boolean): Unit =
          if (bytesDrained &&
            (stateBits & TransportInputHasPendingFlag) == 0 &&
            (stateBits & TransportInputFinishedFlag) == 0 &&
            (stateBits & TransportInputCancelledFlag) == 0 &&
            !hasBeenPulled(inlet))
            pull(inlet)

        def cancel(): Unit =
          if ((stateBits & TransportInputCancelledFlag) == 0) {
            stateBits |= TransportInputCancelledFlag
            pending = null
            clearBytes()
            stateBits &= ~TransportInputHasPendingFlag
            if ((stateBits & TransportInputFinishedFlag) == 0 && !logic.isClosed(inlet)) logic.cancel(inlet)
            stateBits |= TransportInputFinishedFlag
          }

        private def setBytes(next: ByteString): Unit = {
          bytes = next
          bytesOffset = 0
          bytesRemaining = next.size
        }

        private def clearBytes(): Unit = {
          bytes = ByteString.empty
          bytesOffset = 0
          bytesRemaining = 0
        }

        def chopInto(buffer: ByteBuffer): Unit = {
          TlsEngineHelpers.prepareForAppend(buffer)
          drainUnreadBufferInto(buffer)
          if (bytesRemaining == 0 && (stateBits & TransportInputHasPendingFlag) != 0)
            setBytes(dequeue())

          val copied = bytes.copyToBuffer(buffer, bytesOffset)
          if (copied == bytesRemaining) clearBytes()
          else if (copied > 0) {
            bytesOffset += copied
            bytesRemaining -= copied
          }
          buffer.flip()
        }

        private def drainUnreadBufferInto(buffer: ByteBuffer): Unit =
          if (transportUnreadBuffer.hasRemaining) {
            val unreadLimit = transportUnreadBuffer.limit()
            if (transportUnreadBuffer.remaining > buffer.remaining)
              transportUnreadBuffer.limit(transportUnreadBuffer.position() + buffer.remaining)

            buffer.put(transportUnreadBuffer)
            transportUnreadBuffer.limit(unreadLimit)

            if (!transportUnreadBuffer.hasRemaining)
              TlsEngineHelpers.emptyReadBuffer(transportUnreadBuffer)
          }

        def putBackUnreadBuffer(buffer: ByteBuffer): Unit = {
          if (buffer.hasRemaining) {
            TlsEngineHelpers.prepareForAppend(transportUnreadBuffer)
            if (buffer.remaining > transportUnreadBuffer.remaining)
              throw new IllegalStateException(
                s"TLS unread transport data requires ${buffer.remaining} bytes but only " +
                s"${transportUnreadBuffer.remaining} bytes are available")
            transportUnreadBuffer.put(buffer)
            transportUnreadBuffer.flip()
          }
          TlsEngineHelpers.emptyReadBuffer(buffer)
        }
      }

      private final class OutputSlot[T](
          outlet: Outlet[T],
          cancelledFlag: Int,
          completedFlag: Int,
          erroredFlag: Int,
          deferInitialPumpOutput: Boolean = false)
          extends OutHandler {
        private var pending: T = _

        def isCancelled: Boolean = (stateBits & cancelledFlag) != 0
        def isErrored: Boolean = (stateBits & erroredFlag) != 0
        def hasPending: Boolean = pending != null
        def hasDemand: Boolean = !isClosed && isAvailable(outlet)
        def isClosed: Boolean = (stateBits & (cancelledFlag | completedFlag | erroredFlag)) != 0 ||
          logic.isClosed(outlet)
        def isDemandAvailable: Boolean = pending == null && !isClosed && isAvailable(outlet)

        override def onPull(): Unit = {
          if (deferInitialPumpOutput && (stateBits & InitialPumpEnabledFlag) == 0) {
            stateBits |= InitialPumpEnabledFlag
            stateBits |= InitialPumpDeferringOutputFlag
            runPump()
            stateBits &= ~InitialPumpDeferringOutputFlag
            if (pending != null && isAvailable(outlet))
              scheduleOnce(InitialOutputFlushTimer, Duration.Zero)
          } else {
            pushPending()
            completeStageIfReady()
            runPump()
          }
        }

        def pushPending(): Unit =
          if (pending != null &&
            (stateBits & (cancelledFlag | erroredFlag)) == 0 &&
            !logic.isClosed(outlet) &&
            isAvailable(outlet)) {
            val elem = pending
            pending = null.asInstanceOf[T]
            push(outlet, elem)
            if ((stateBits & completedFlag) != 0 && !logic.isClosed(outlet))
              logic.complete(outlet)
          }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          onCancel()
          completeStageIfReady()
          runPump()
        }

        def enqueue(elem: T): Unit =
          if (!isClosed) {
            if (pending == null && isAvailable(outlet) && !shouldDeferInitialOutput) push(outlet, elem)
            else if (pending == null) pending = elem
            else throw new IllegalStateException("TLS output slot already has pending data")
          }

        private def shouldDeferInitialOutput: Boolean =
          deferInitialPumpOutput && (stateBits & InitialPumpDeferringOutputFlag) != 0

        def onCancel(): Unit = {
          stateBits |= cancelledFlag
          pending = null.asInstanceOf[T]
        }

        def completeOutput(): Unit =
          if (!isClosed) {
            stateBits |= completedFlag
            if (pending == null)
              logic.complete(outlet)
          }

        def failOutput(ex: Throwable): Unit =
          if (!isClosed) {
            stateBits |= erroredFlag
            logic.fail(outlet, ex)
          }
      }
    }
}

@InternalApi private[pekko] object TlsGraphStage {
  private[pekko] final val TransportFlushTimer = "TlsGraphStageTransportFlush"
  private[pekko] final val UserInputBatchTimer = "TlsGraphStageUserInputBatch"
  private[pekko] final val InitialOutputFlushTimer = "TlsGraphStageInitialOutputFlush"
  private[pekko] final val MaxInlineEngineProgressAttempts = 1 << 10
  private[pekko] final val MaxApplicationRecordsPerEngineCall = 4
  private[pekko] final val MaxBatchedApplicationBytes = 1024
  private[pekko] final val DeferredTransportFlushLimit = 16
  private[pekko] final val InputBufferInitialSize = 16
  private[pekko] final val InputBufferMaxSize = 64
  private[pekko] final val UserInputQueueSize = InputBufferMaxSize
  private[pekko] final val UserInputQueueMask = UserInputQueueSize - 1
  private[pekko] val StreamTlsAttributes: Attributes =
    Attributes.asyncBoundary and Attributes.inputBuffer(InputBufferInitialSize, InputBufferMaxSize)

  private[pekko] final val PlainDataAllowedFlag = 1 << 0
  private[pekko] final val StageClosedFlag = 1 << 1
  private[pekko] final val InitialPumpEnabledFlag = 1 << 2
  private[pekko] final val TransportFlushPendingFlag = 1 << 3
  private[pekko] final val UserInputHasPendingFlag = 1 << 4
  private[pekko] final val UserInputFinishedFlag = 1 << 5
  private[pekko] final val UserInputCancelledFlag = 1 << 6
  private[pekko] final val TransportInputHasPendingFlag = 1 << 7
  private[pekko] final val TransportInputFinishedFlag = 1 << 8
  private[pekko] final val TransportInputCancelledFlag = 1 << 9
  private[pekko] final val UserOutputCancelledFlag = 1 << 10
  private[pekko] final val UserOutputCompletedFlag = 1 << 11
  private[pekko] final val UserOutputErroredFlag = 1 << 12
  private[pekko] final val TransportOutputCancelledFlag = 1 << 13
  private[pekko] final val TransportOutputCompletedFlag = 1 << 14
  private[pekko] final val TransportOutputErroredFlag = 1 << 15
  private[pekko] final val StageCompletionPendingFlag = 1 << 16
  private[pekko] final val UserInputBatchPendingFlag = 1 << 17
  private[pekko] final val InitialPumpDeferringOutputFlag = 1 << 18

  private[pekko] final val BidirectionalPhase = 1
  private[pekko] final val FlushingOutboundPhase = 2
  private[pekko] final val AwaitingClosePhase = 3
  private[pekko] final val OutboundClosedPhase = 4
  private[pekko] final val InboundClosedPhase = 5
  private[pekko] final val CompletedPhase = 6
}
