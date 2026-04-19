/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.io

import java.nio.ByteBuffer
import javax.net.ssl._
import javax.net.ssl.SSLEngineResult.HandshakeStatus
import javax.net.ssl.SSLEngineResult.HandshakeStatus._
import javax.net.ssl.SSLEngineResult.Status._

import scala.annotation.tailrec
import scala.collection.immutable
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.{ Completed, TransferPhase, TransferState }
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/**
 * INTERNAL API.
 */
@InternalApi private[stream] object TlsGraphStage {
  final val UseLegacyActorPath = "pekko.stream.materializer.tls.use-legacy-actor"
  final val DefaultDispatcher = "pekko.stream.materializer.dispatcher"

  def useLegacyActorPath: Boolean =
    sys.props.get(UseLegacyActorPath) match {
      case Some(value) =>
        value.trim.toLowerCase match {
          case "false" | "off" | "0" | "no" => false
          case _                             => true
        }
      case None => true
    }
}

/**
 * INTERNAL API.
 */
@InternalApi
private[stream] final class TlsGraphStage(
    createSSLEngine: () => SSLEngine,
    verifySession: SSLSession => Try[Unit],
    closing: TLSClosing,
    plainInputFailure: () => Option[Throwable],
    cipherInputFailure: () => Option[Throwable])
    extends GraphStage[BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound]] {
  import TlsGraphStage._

  val plainIn: Inlet[SslTlsOutbound] = Inlet("TlsGraphStage.plainIn")
  val plainOut: Outlet[SslTlsInbound] = Outlet("TlsGraphStage.plainOut")
  val cipherIn: Inlet[ByteString] = Inlet("TlsGraphStage.cipherIn")
  val cipherOut: Outlet[ByteString] = Outlet("TlsGraphStage.cipherOut")

  override val shape: BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound] =
    BidiShape(plainIn, cipherOut, cipherIn, plainOut)

  // The dispatcher attribute doubles as an async boundary, which ensures that the
  // TLS state machine is materialized into its own ActorGraphInterpreter actor.
  override protected def initialAttributes: Attributes =
    Attributes.name("TlsGraphStage") and ActorAttributes.dispatcher(DefaultDispatcher)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private val maxTLSIterations = 1000

      private var stopped = false
      private var pumping = false
      private var pumpAgain = false
      private var startupPending = true
      private var bridgeFailureCheckActive = true
      private var completing = false

      private var unwrapPutBackCounter = 0
      private var lastHandshakeStatus: HandshakeStatus = _
      private var corkUser = true

      private var userInFinished = false
      private var transportInFinished = false
      private var userOutCancelled = false
      private var transportOutCancelled = false
      private var userOutTerminated = false
      private var transportOutTerminated = false

      private var pendingUserIn: Option[SslTlsOutbound] = None
      private var pendingTransportIn: Option[ByteString] = None

      // FIFO buffer for decrypted user output.
      //
      // During TLS handshake the VirtualProcessor bridges connecting the inner TLS sub-graph
      // to the outer stream may not have propagated demand to plainOut yet. Requiring
      // isAvailable(plainOut) as a precondition for inbound processing would therefore
      // dead-lock the handshake. Instead we decouple inbound processing from plainOut
      // demand by buffering pending user messages here. userOutAvailable keeps the pump
      // from re-entering inbound processing while buffered output still needs to be drained.
      private var pendingUserOut = immutable.Queue.empty[SslTlsInbound]

      // Symmetric FIFO buffer for encrypted transport output.
      //
      // The inner TLS stage can need to emit handshake bytes (for example the
      // initial ClientHello) before the outer VirtualProcessor bridge has
      // propagated demand to cipherOut. Buffering one element here avoids that
      // startup race and lets the handshake make forward progress. As with
      // pendingUserOut, transportOutAvailable keeps the pump from generating more
      // transport output while buffered bytes still need to be drained.
      private var pendingTransportOut = immutable.Queue.empty[ByteString]

      private val transportOutBuffer = ByteBuffer.allocate(16665 + 2048)
      private val userOutBuffer = ByteBuffer.allocate(16665 * 2 + 2048)
      private val transportInBuffer = ByteBuffer.allocate(16665 + 2048)
      private val userInBuffer = ByteBuffer.allocate(16665 + 2048)

      private var engine: SSLEngine = _
      private var currentSession: SSLSession = _

      private class ChoppingBlock(
          name: String,
          pendingElement: => Option[Any],
          isDepleted: => Boolean,
          refill: Any => Unit)
          extends TransferState {
        override def isReady: Boolean = buffer.nonEmpty || pendingElement.nonEmpty || isDepleted
        override def isCompleted: Boolean = false

        private var buffer = ByteString.empty

        def isEmpty: Boolean = buffer.isEmpty

        def chopInto(b: ByteBuffer): Unit = {
          b.compact()
          if (buffer.isEmpty) {
            pendingElement.foreach { next =>
              next match {
                case bs: ByteString         => buffer = bs
                case SendBytes(bs)          => buffer = bs
                case n: NegotiateNewSession =>
                  setNewSessionParameters(n)
                  buffer = ByteString.empty
                case other =>
                  throw new IllegalStateException(s"Unexpected TLS input element [$other] on $name")
              }
              refill(next)
            }
          }

          val copied = buffer.copyToBuffer(b)
          buffer = buffer.drop(copied)
          b.flip()
        }

        def putBack(b: ByteBuffer): Unit =
          if (b.hasRemaining) {
            val bs = ByteString(b)
            if (bs.nonEmpty) buffer = bs ++ buffer
            prepare(b)
          }

        def prepare(b: ByteBuffer): Unit = {
          b.clear()
          b.limit(0)
        }
      }

      private val userInChoppingBlock =
        new ChoppingBlock(
          "UserIn",
          pendingUserIn,
          isUserInDepleted,
          _ => {
            pendingUserIn = None
            tryPullPlainInIfNeeded()
          })
      userInChoppingBlock.prepare(userInBuffer)

      private val transportInChoppingBlock =
        new ChoppingBlock(
          "TransportIn",
          pendingTransportIn,
          isTransportInDepleted,
          _ => {
            pendingTransportIn = None
            tryPullCipherInIfNeeded()
          })
      transportInChoppingBlock.prepare(transportInBuffer)

      private val transportOutAvailable = new TransferState {
        override def isReady: Boolean = !transportOutTerminated && pendingTransportOut.isEmpty
        override def isCompleted: Boolean = transportOutTerminated
      }

      private val userOutAvailable = new TransferState {
        // Ready when no buffered user output is waiting to be drained.
        // This decouples inbound processing from whether the downstream has already
        // pulled plainOut while still avoiding unbounded re-entry once output has queued up.
        override def isReady: Boolean = !userOutTerminated && pendingUserOut.isEmpty
        override def isCompleted: Boolean = userOutTerminated
      }

      private val engineNeedsWrap = new TransferState {
        override def isReady: Boolean = lastHandshakeStatus == NEED_WRAP
        override def isCompleted: Boolean = engine.isOutboundDone
      }

      private val engineInboundOpen = new TransferState {
        override def isReady: Boolean = true
        override def isCompleted: Boolean = engine.isInboundDone
      }

      // Match TLSActor.awaitingClose semantics: this phase should only run when a
      // fresh transport input element is pending. Once transport input is depleted
      // it must complete instead of repeatedly unwrapping an empty buffer.
      private val transportInPending = new TransferState {
        override def isReady: Boolean = pendingTransportIn.nonEmpty
        override def isCompleted: Boolean = isTransportInDepleted
      }

      private def mayStartRenegotiation: Boolean =
        lastHandshakeStatus match {
          case HandshakeStatus.NOT_HANDSHAKING | HandshakeStatus.FINISHED => true
          case _                                                          => false
        }

      private def userInputReadyForWrap: Boolean =
        if (!userInChoppingBlock.isEmpty) true
        else
          pendingUserIn match {
            case Some(_: NegotiateNewSession) => mayStartRenegotiation
            case Some(_)                      => true
            case None                         => false
          }

      private val userHasData = new TransferState {
        override def isReady: Boolean =
          !corkUser && userInputReadyForWrap && lastHandshakeStatus != NEED_UNWRAP
        override def isCompleted: Boolean = isUserInDepleted
      }

      private val userOutCancelledState = new TransferState {
        override def isReady: Boolean = userOutCancelled
        override def isCompleted: Boolean = engine.isInboundDone || userOutTerminated
      }

      private val outbound = (userHasData || engineNeedsWrap) && transportOutAvailable
      private val inbound = (transportInChoppingBlock && userOutAvailable) || userOutCancelledState

      private val outboundHalfClosed = engineNeedsWrap && transportOutAvailable
      private val inboundHalfClosed = transportInChoppingBlock && engineInboundOpen

      // Detects the case where user input was depleted before or during the TLS handshake.
      //
      // When the server role completes the handshake by wrapping its own Finished message
      // (inside doWrap → doOutbound → bidirectional.action), corkUser is set to false
      // and lastHandshakeStatus becomes NOT_HANDSHAKING. After doWrap returns, neither
      // outbound (no data to wrap, engine no longer NEED_WRAP) nor inbound (no pending
      // transport data) is ready, so the pump loop exits without calling doOutbound again.
      // Without this extra predicate, doOutbound would never see isUserInDepleted=true and
      // mayCloseOutbound=true in the same pump tick, and the stage would hang waiting for
      // an event that never arrives.
      //
      // This condition is intentionally independent of transportOutAvailable: the decision
      // to close the outbound (engine.closeOutbound + nextPhase(outboundClosed)) does not
      // write to the transport buffer immediately; the close_notify is sent later via
      // doWrap inside the outboundClosed phase.
      private val shouldCloseOutbound = new TransferState {
        override def isReady: Boolean =
          !corkUser && isUserInDepleted && userInChoppingBlock.isEmpty && mayCloseOutbound
        override def isCompleted: Boolean = false
      }

      private val bidirectional = TransferPhase(outbound || shouldCloseOutbound || inbound) { () =>
        val continue = doInbound(isOutboundClosed = false, inbound)
        if (continue) doOutbound(isInboundClosed = false)
      }

      private val flushingOutbound = TransferPhase(outboundHalfClosed) { () =>
        try doWrap()
        catch { case _: SSLException => nextPhase(completedPhase) }
      }

      private val awaitingClose = TransferPhase(transportInPending && engineInboundOpen) { () =>
        transportInChoppingBlock.chopInto(transportInBuffer)
        try doUnwrap(ignoreOutput = true)
        catch { case _: SSLException => nextPhase(completedPhase) }
      }

      private val outboundClosed = TransferPhase(outboundHalfClosed || inbound) { () =>
        val continue = doInbound(isOutboundClosed = true, inbound)
        if (continue && outboundHalfClosed.isReady) {
          try doWrap()
          catch { case _: SSLException => nextPhase(completedPhase) }
        }
      }

      private val inboundClosed = TransferPhase(outbound || inboundHalfClosed) { () =>
        val continue = doInbound(isOutboundClosed = false, inboundHalfClosed)
        if (continue) doOutbound(isInboundClosed = true)
      }

      private val completedPhase = TransferPhase(Completed) { () =>
        throw new IllegalStateException("The action of completed phase must never be executed")
      }

      private var transferState: TransferState = bidirectional.precondition
      private var currentAction: () => Unit = bidirectional.action
      private val pumpAsync = getAsyncCallback[Unit] { _ =>
        startupPending = false
        pump()
      }
      private val drainTransportOutAsync = getAsyncCallback[Unit](_ => drainTransportOut())
      private val drainUserOutAsync = getAsyncCallback[Unit](_ => drainUserOut())

      setHandler(plainIn, new InHandler {
        override def onPush(): Unit = {
          pendingUserIn = Some(grab(plainIn))
          pump()
        }

        override def onUpstreamFinish(): Unit = {
          userInFinished = true
          pump()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = failTls(ex)
      })

      setHandler(cipherIn, new InHandler {
        override def onPush(): Unit = {
          pendingTransportIn = Some(grab(cipherIn))
          pump()
        }

        override def onUpstreamFinish(): Unit = {
          transportInFinished = true
          pump()
        }

        override def onUpstreamFailure(ex: Throwable): Unit = failTls(ex)
      })

      setHandler(cipherOut, new OutHandler {
        override def onPull(): Unit =
          if (!startupPending) {
            if (!drainTransportOut()) {
              if (completing) tryCompleteStage()
              else pump()
            }
          }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          transportOutCancelled = true
          transportOutTerminated = true
          pendingTransportOut = immutable.Queue.empty
          pump()
        }
      })

      setHandler(plainOut, new OutHandler {
        override def onPull(): Unit = {
          // Flush any buffered user output before running the pump.
          // This drains pendingUserOut (set by enqueueUser when isAvailable(plainOut) was false)
          // and re-enables inbound processing (userOutAvailable.isReady = pendingUserOut.isEmpty).
          if (!startupPending) {
            if (!drainUserOut()) {
              if (completing) tryCompleteStage()
              else pump()
            }
          }
        }

        override def onDownstreamFinish(cause: Throwable): Unit = {
          userOutCancelled = true
          pendingUserOut = immutable.Queue.empty
          pump()
        }
      })

      override def preStart(): Unit = {
        try {
          engine = createSSLEngine()
          engine.beginHandshake()
          lastHandshakeStatus = engine.getHandshakeStatus
          currentSession = engine.getSession
          tryPullPlainInIfNeeded()
          tryPullCipherInIfNeeded()
          pumpAsync.invoke(())
        } catch {
          case NonFatal(ex) => failStage(ex)
        }
      }

      override def postStop(): Unit = {
        stopped = true
        super.postStop()
      }

      private def nextPhase(phase: TransferPhase): Unit = {
        transferState = phase.precondition
        currentAction = phase.action
      }

      private def isUserInDepleted: Boolean = userInFinished && pendingUserIn.isEmpty
      private def isTransportInDepleted: Boolean = transportInFinished && pendingTransportIn.isEmpty

      private def tryPullPlainInIfNeeded(): Unit =
        if (pendingUserIn.isEmpty && !isClosed(plainIn) && !hasBeenPulled(plainIn)) pull(plainIn)

      private def tryPullCipherInIfNeeded(): Unit =
        if (pendingTransportIn.isEmpty && !isClosed(cipherIn) && !hasBeenPulled(cipherIn)) pull(cipherIn)

      private def completeOrFlush(): Unit =
        if (engine.isOutboundDone || (engine.isInboundDone && userInChoppingBlock.isEmpty)) nextPhase(completedPhase)
        else nextPhase(flushingOutbound)

      private def pollBridgedInputFailures(): Boolean =
        if (bridgeFailureCheckActive)
          plainInputFailure().orElse(cipherInputFailure()) match {
            case Some(ex) =>
              failTls(ex)
              true
            case None => false
          }
        else false

      private def doInbound(isOutboundClosed: Boolean, inboundState: TransferState): Boolean =
        if (isTransportInDepleted && transportInChoppingBlock.isEmpty) {
          try engine.closeInbound()
          catch {
            case ex: SSLException =>
              if (corkUser) {
                failTls(ex)
                nextPhase(completedPhase)
                return false
              } else enqueueUser(SessionTruncated)
          }
          lastHandshakeStatus = engine.getHandshakeStatus
          completeOrFlush()
          false
        } else if ((inboundState ne inboundHalfClosed) && userOutCancelled) {
          if (!isOutboundClosed && closing.ignoreCancel) nextPhase(inboundClosed)
          else {
            engine.closeOutbound()
            lastHandshakeStatus = engine.getHandshakeStatus
            nextPhase(flushingOutbound)
          }
          true
        } else if (inboundState.isReady) {
          transportInChoppingBlock.chopInto(transportInBuffer)
          try {
            doUnwrap(ignoreOutput = false)
            true
          } catch {
            case ex: SSLException =>
              failTls(ex, closeTransport = false)
              // After a handshake failure (e.g. certificate_unknown), the SSLEngine buffers
              // a TLS fatal alert that must be wrapped and sent to the peer BEFORE we tear
              // down the connection. Without flushing it the peer's engine only sees a TCP
              // close and throws "closing inbound before receiving peer's close_notify"
              // instead of the actual alert. We refresh lastHandshakeStatus here so that
              // the engineNeedsWrap predicate correctly reflects the engine's current state.
              lastHandshakeStatus = engine.getHandshakeStatus
              if (engineNeedsWrap.isReady) {
                // Engine has a TLS alert queued. Flush it via flushingOutbound;
                // doWrap() will transition to completedPhase once the engine reports CLOSED.
                nextPhase(flushingOutbound)
              } else {
                engine.closeInbound()
                completeOrFlush()
              }
              false
          }
        } else true

      private def doOutbound(isInboundClosed: Boolean): Unit =
        if (isUserInDepleted && userInChoppingBlock.isEmpty && mayCloseOutbound) {
          if (!isInboundClosed && closing.ignoreComplete) {
            nextPhase(outboundClosed)
          } else {
            engine.closeOutbound()
            lastHandshakeStatus = engine.getHandshakeStatus
            nextPhase(outboundClosed)
          }
        } else if (transportOutCancelled) {
          nextPhase(completedPhase)
        } else if (outbound.isReady) {
          if (userHasData.isReady) userInChoppingBlock.chopInto(userInBuffer)
          try doWrap()
          catch {
            case ex: SSLException =>
              failTls(ex, closeTransport = false)
              // After a handshake failure (e.g. certificate_unknown), the first engine.wrap()
              // throws with the cert error but leaves the engine in NEED_WRAP state.
              // A second wrap() call produces the actual TLS fatal-alert bytes that must
              // reach the peer. Without sending them, the peer only sees a TCP close and
              // reports "closing inbound before receiving peer's close_notify" instead of
              // the real error.
              lastHandshakeStatus = engine.getHandshakeStatus
              if (engineNeedsWrap.isReady) {
                try doWrap() // flushes the TLS alert into pendingTransportOut
                catch { case _: SSLException => } // ignore any secondary exception
              }
              completeOrFlush()
          }
        }

      private def mayCloseOutbound: Boolean =
        lastHandshakeStatus match {
          case HandshakeStatus.NOT_HANDSHAKING | HandshakeStatus.FINISHED => true
          case _                                                          => false
        }

      private def enqueueTransport(bytes: ByteString): Unit =
        if (!transportOutTerminated) {
          pendingTransportOut = pendingTransportOut.enqueue(bytes)
          drainTransportOutAsync.invoke(())
        }

      private def enqueueUser(message: SslTlsInbound): Unit =
        if (!userOutCancelled && !userOutTerminated) {
          pendingUserOut = pendingUserOut.enqueue(message)
          drainUserOutAsync.invoke(())
        }

      private def drainTransportOut(): Boolean =
        if (
          !startupPending &&
          !transportOutTerminated &&
          !pollBridgedInputFailures() &&
          isAvailable(cipherOut) &&
          pendingTransportOut.nonEmpty) {
          val (bytes, remaining) = pendingTransportOut.dequeue
          pendingTransportOut = remaining
          push(cipherOut, bytes)
          true
        } else false

      private def drainUserOut(): Boolean =
        if (
          !startupPending &&
          !userOutTerminated &&
          !pollBridgedInputFailures() &&
          isAvailable(plainOut) &&
          pendingUserOut.nonEmpty) {
          val (msg, remaining) = pendingUserOut.dequeue
          pendingUserOut = remaining
          push(plainOut, msg)
          true
        } else false

      private def flushToTransport(): Unit = {
        transportOutBuffer.flip()
        if (transportOutBuffer.hasRemaining) enqueueTransport(ByteString(transportOutBuffer))
        transportOutBuffer.clear()
      }

      private def flushToUser(): Unit = {
        if (unwrapPutBackCounter > 0) unwrapPutBackCounter = 0
        userOutBuffer.flip()
        if (userOutBuffer.hasRemaining) enqueueUser(SessionBytes(currentSession, ByteString(userOutBuffer)))
        userOutBuffer.clear()
      }

      private def doWrap(): Unit = {
        val result = engine.wrap(userInBuffer, transportOutBuffer)
        lastHandshakeStatus = result.getHandshakeStatus

        if (lastHandshakeStatus == FINISHED) handshakeFinished()
        runDelegatedTasks()

        result.getStatus match {
          case OK =>
            if (transportOutBuffer.position() == 0 && lastHandshakeStatus == NEED_WRAP)
              throw new IllegalStateException("SSLEngine trying to loop NEED_WRAP without producing output")

            flushToTransport()
            userInChoppingBlock.putBack(userInBuffer)
          case CLOSED =>
            flushToTransport()
            if (engine.isInboundDone) nextPhase(completedPhase)
            else nextPhase(awaitingClose)
          case status =>
            failTls(new IllegalStateException(s"unexpected status $status in doWrap()"))
        }
      }

      @tailrec
      private def doUnwrap(ignoreOutput: Boolean): Unit = {
        val oldInPosition = transportInBuffer.position()
        val result = engine.unwrap(transportInBuffer, userOutBuffer)
        if (ignoreOutput) userOutBuffer.clear()
        lastHandshakeStatus = result.getHandshakeStatus
        runDelegatedTasks()

        result.getStatus match {
          case OK =>
            result.getHandshakeStatus match {
              case NEED_WRAP =>
                // Keep the legacy guard from TLSActor: SyncProcessingLimit does not
                // protect against an SSLEngine loop that stays within a single callback.
                unwrapPutBackCounter += 1
                if (unwrapPutBackCounter > maxTLSIterations) {
                  throw new IllegalStateException(
                    s"Stuck in unwrap loop, bailing out, last handshake status [$lastHandshakeStatus], " +
                    s"remaining=${transportInBuffer.remaining}, out=${userOutBuffer.position()}, " +
                    "(https://github.com/apache/pekko/issues/442)")
                }
                transportInChoppingBlock.putBack(transportInBuffer)
              case FINISHED =>
                flushToUser()
                handshakeFinished()
                transportInChoppingBlock.putBack(transportInBuffer)
              case NEED_UNWRAP
                  if transportInBuffer.hasRemaining &&
                  userOutBuffer.position() == 0 &&
                  transportInBuffer.position() == oldInPosition =>
                throw new IllegalStateException("SSLEngine trying to loop NEED_UNWRAP without producing output")
              case _ =>
                if (transportInBuffer.hasRemaining) doUnwrap(ignoreOutput = false)
                else flushToUser()
            }
          case CLOSED =>
            flushToUser()
            completeOrFlush()
          case BUFFER_UNDERFLOW =>
            flushToUser()
          case BUFFER_OVERFLOW =>
            flushToUser()
            transportInChoppingBlock.putBack(transportInBuffer)
          case null =>
            failTls(new IllegalStateException("unexpected status 'null' in doUnwrap()"))
        }
      }

      @tailrec
      private def runDelegatedTasks(): Unit = {
        val task = engine.getDelegatedTask
        if (task ne null) {
          task.run()
          runDelegatedTasks()
        } else {
          lastHandshakeStatus = engine.getHandshakeStatus
        }
      }

      private def handshakeFinished(): Unit = {
        val session = engine.getSession
        verifySession(session) match {
          case Success(()) =>
            currentSession = session
            corkUser = false
            flushToUser()
          case Failure(ex) =>
            failTls(ex, closeTransport = true)
        }
      }

      private def setNewSessionParameters(params: NegotiateNewSession): Unit = {
        currentSession.invalidate()
        TlsUtils.applySessionParameters(engine, params)
        engine.beginHandshake()
        lastHandshakeStatus = engine.getHandshakeStatus
        corkUser = true
      }

      private def failTls(e: Throwable, closeTransport: Boolean = true): Unit = {
        if (!stopped) {
          stopped = true
          cancelInputs(clearPendingUserOut = true, clearPendingTransportOut = true)
          failUserOut(e)
          if (closeTransport) failTransportOut(e)
        }
      }

      private def cancelInputs(clearPendingUserOut: Boolean, clearPendingTransportOut: Boolean): Unit = {
        pendingUserIn = None
        pendingTransportIn = None
        if (clearPendingUserOut) {
          pendingUserOut = immutable.Queue.empty
        }
        if (clearPendingTransportOut) {
          pendingTransportOut = immutable.Queue.empty
        }
        if (!isClosed(plainIn)) cancel(plainIn)
        if (!isClosed(cipherIn)) cancel(cipherIn)
      }

      private def tryCompleteStage(): Unit =
        if (
          completing &&
          (transportOutTerminated || pendingTransportOut.isEmpty) &&
          (userOutCancelled || userOutTerminated || pendingUserOut.isEmpty)) {
          completeOutputs()
          completeStage()
        }

      private def failTransportOut(e: Throwable): Unit =
        if (!transportOutTerminated) {
          transportOutTerminated = true
          fail(cipherOut, e)
        }

      private def failUserOut(e: Throwable): Unit =
        if (!userOutCancelled && !userOutTerminated) {
          userOutTerminated = true
          fail(plainOut, e)
        }

      private def completeOutputs(): Unit = {
        if (!transportOutTerminated) {
          transportOutTerminated = true
          complete(cipherOut)
        }
        if (!userOutCancelled && !userOutTerminated) {
          userOutTerminated = true
          complete(plainOut)
        }
      }

      private def pump(): Unit = {
        if (pumping) {
          pumpAgain = true
          return
        }

        pumping = true
        try {
          do {
            pumpAgain = false
            if (pollBridgedInputFailures()) return
            tryPullPlainInIfNeeded()
            tryPullCipherInIfNeeded()

            while (transferState.isExecutable) currentAction()
          } while (pumpAgain)
        } catch {
          case NonFatal(ex) =>
            failTls(ex)
        } finally {
          if (bridgeFailureCheckActive) bridgeFailureCheckActive = false
          pumping = false
        }

        if (transferState.isCompleted) {
          if (!completing) {
            completing = true
            cancelInputs(clearPendingUserOut = false, clearPendingTransportOut = false)
          }
          if (!drainTransportOut()) drainUserOut()
          tryCompleteStage()
        }
      }
    }
}
