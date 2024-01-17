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

package org.apache.pekko.cluster.ddata

import java.util.concurrent.ThreadLocalRandom

import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.ActorSystem
import pekko.actor.Props

/**
 * This "sample" simulates lots of data entries, and can be used for
 * optimizing replication (e.g. catch-up when adding more nodes).
 */
object LotsOfDataBot {

  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("7354", "7355", "0"))
    else
      startup(args.toIndexedSeq)
  }

  def startup(ports: Seq[String]): Unit = {
    ports.foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory
        .parseString("pekko.remote.classic.netty.tcp.port=" + port)
        .withFallback(
          ConfigFactory.load(ConfigFactory.parseString("""
            passive = off
            max-entries = 100000
            pekko.actor.provider = "cluster"
            pekko.remote {
              artery.canonical {
                hostname = "127.0.0.1"
                port = 0
              }
            }

            pekko.cluster {
              seed-nodes = [
                "pekko://ClusterSystem@127.0.0.1:7354",
                "pekko://ClusterSystem@127.0.0.1:7355"]

              downing-provider-class = org.apache.pekko.cluster.testkit.AutoDowning
              testkit.auto-down-unreachable-after = 10s
            }
            """)))

      // Create a Pekko system
      val system = ActorSystem("ClusterSystem", config)
      // Create an actor that handles cluster domain events
      system.actorOf(Props[LotsOfDataBot](), name = "dataBot")
    }
  }

  private case object Tick

}

class LotsOfDataBot extends Actor with ActorLogging {
  import LotsOfDataBot._
  import Replicator._

  implicit val selfUniqueAddress: SelfUniqueAddress = DistributedData(context.system).selfUniqueAddress
  val replicator = DistributedData(context.system).replicator

  import context.dispatcher
  val isPassive = context.system.settings.config.getBoolean("passive")
  var tickTask =
    if (isPassive)
      context.system.scheduler.scheduleWithFixedDelay(1.seconds, 1.seconds, self, Tick)
    else
      context.system.scheduler.scheduleWithFixedDelay(20.millis, 20.millis, self, Tick)

  val startTime = System.nanoTime()
  var count = 1L
  val maxEntries = context.system.settings.config.getInt("max-entries")

  def receive = if (isPassive) passive else active

  def active: Receive = {
    case Tick =>
      val loop = if (count >= maxEntries) 1 else 100
      for (_ <- 1 to loop) {
        count += 1
        if (count % 10000 == 0)
          log.info("Reached {} entries", count)
        if (count == maxEntries) {
          log.info("Reached {} entries", count)
          tickTask.cancel()
          tickTask = context.system.scheduler.scheduleWithFixedDelay(1.seconds, 1.seconds, self, Tick)
        }
        val key = ORSetKey[String]((count % maxEntries).toString)
        if (count <= 100)
          replicator ! Subscribe(key, self)
        val s = ThreadLocalRandom.current().nextInt(97, 123).toChar.toString
        if (count <= maxEntries || ThreadLocalRandom.current().nextBoolean()) {
          // add
          replicator ! Update(key, ORSet(), WriteLocal)(_ :+ s)
        } else {
          // remove
          replicator ! Update(key, ORSet(), WriteLocal)(_.remove(s))
        }
      }

    case _: UpdateResponse[?] => // ignore
    case c @ Changed(ORSetKey(id)) =>
      val elements = c.dataValue match {
        case ORSet(e) => e
        case _        => throw new RuntimeException()
      }
      log.info("Current elements: {} -> {}", id, elements)
  }

  def passive: Receive = {
    case Tick =>
      if (!tickTask.isCancelled)
        replicator ! GetKeyIds
    case GetKeyIdsResult(keys) =>
      if (keys.size >= maxEntries) {
        tickTask.cancel()
        val duration = (System.nanoTime() - startTime).nanos.toMillis
        log.info("It took {} ms to replicate {} entries", duration, keys.size)
      }
    case c @ Changed(ORSetKey(id)) =>
      val elements = c.dataValue match {
        case ORSet(e) => e
        case _        => throw new RuntimeException()
      }
      log.info("Current elements: {} -> {}", id, elements)
  }

  override def postStop(): Unit = tickTask.cancel()

}
