/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import java.util.concurrent.ConcurrentHashMap

import scala.annotation.tailrec

import scala.annotation.nowarn

import org.apache.pekko
import pekko.actor.ActorSelectionMessage
import pekko.actor.ActorSystem
import pekko.actor.ClassicActorSystemProvider
import pekko.actor.ExtendedActorSystem
import pekko.actor.Extension
import pekko.actor.ExtensionId
import pekko.actor.ExtensionIdProvider
import pekko.event.Logging
import pekko.routing.RouterEnvelope

/**
 * INTERNAL API
 * Extension that keeps track of remote metrics, such
 * as max size of different message types.
 */
@nowarn("msg=deprecated")
private[pekko] object RemoteMetricsExtension extends ExtensionId[RemoteMetrics] with ExtensionIdProvider {
  override def get(system: ActorSystem): RemoteMetrics = super.get(system)
  override def get(system: ClassicActorSystemProvider): RemoteMetrics = super.get(system)

  override def lookup = RemoteMetricsExtension

  override def createExtension(system: ExtendedActorSystem): RemoteMetrics =
    if (RARP(system).provider.remoteSettings.LogFrameSizeExceeding.isEmpty)
      new RemoteMetricsOff
    else
      new RemoteMetricsOn(system)
}

/**
 * INTERNAL API
 */
private[pekko] trait RemoteMetrics extends Extension {

  /**
   * Logging of the size of different message types.
   * Maximum detected size per message type is logged once, with
   * and increase threshold of 10%.
   */
  def logPayloadBytes(msg: Any, payloadBytes: Int): Unit
}

/**
 * INTERNAL API
 */
private[pekko] class RemoteMetricsOff extends RemoteMetrics {
  override def logPayloadBytes(msg: Any, payloadBytes: Int): Unit = ()
}

/**
 * INTERNAL API
 */
@nowarn("msg=deprecated")
private[pekko] class RemoteMetricsOn(system: ExtendedActorSystem) extends RemoteMetrics {

  private val logFrameSizeExceeding: Int =
    RARP(system).provider.remoteSettings.LogFrameSizeExceeding.getOrElse(Int.MaxValue)
  private val log = Logging(system, classOf[RemoteMetrics])
  private val maxPayloadBytes: ConcurrentHashMap[Class[_], Integer] = new ConcurrentHashMap

  override def logPayloadBytes(msg: Any, payloadBytes: Int): Unit =
    if (payloadBytes >= logFrameSizeExceeding) {
      val clazz = msg match {
        case x: ActorSelectionMessage => x.msg.getClass
        case x: RouterEnvelope        => x.message.getClass
        case _                        => msg.getClass
      }

      // 10% threshold until next log
      def newMax = (payloadBytes * 1.1).toInt

      @tailrec def check(): Unit = {
        val max = maxPayloadBytes.get(clazz)
        if (max eq null) {
          if (maxPayloadBytes.putIfAbsent(clazz, newMax) eq null)
            log.info("Payload size for [{}] is [{}] bytes", clazz.getName, payloadBytes)
          else check()
        } else if (payloadBytes > max) {
          if (maxPayloadBytes.replace(clazz, max, newMax))
            log.info("New maximum payload size for [{}] is [{}] bytes", clazz.getName, payloadBytes)
          else check()
        }
      }
      check()
    }
}
