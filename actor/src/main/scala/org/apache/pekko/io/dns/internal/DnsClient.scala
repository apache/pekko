/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns.internal

import java.net.{ InetAddress, InetSocketAddress }
import scala.collection.{ immutable => im }
import scala.concurrent.duration._
import scala.util.Try
import scala.annotation.{ nowarn, tailrec }
import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, ActorRef, NoSerializationVerificationNeeded, Props, Stash }
import pekko.actor.Status.Failure
import pekko.annotation.InternalApi
import pekko.io.{ IO, Tcp, Udp }
import pekko.io.dns.{ RecordClass, RecordType, ResourceRecord }
import pekko.pattern.{ BackoffOpts, BackoffSupervisor }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object DnsClient {
  sealed trait DnsQuestion {
    def id: Short
    def name: String
    def withId(newId: Short): DnsQuestion = {
      this match {
        case SrvQuestion(_, name) => SrvQuestion(newId, name)
        case Question4(_, name)   => Question4(newId, name)
        case Question6(_, name)   => Question6(newId, name)
      }
    }
  }
  final case class SrvQuestion(id: Short, name: String) extends DnsQuestion
  final case class Question4(id: Short, name: String) extends DnsQuestion
  final case class Question6(id: Short, name: String) extends DnsQuestion
  final case class Answer(id: Short, rrs: im.Seq[ResourceRecord], additionalRecs: im.Seq[ResourceRecord] = Nil)
      extends NoSerializationVerificationNeeded

  final case class DuplicateId(id: Short) extends NoSerializationVerificationNeeded
  final case class DropRequest(question: DnsQuestion)
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class DnsClient(ns: InetSocketAddress) extends Actor with ActorLogging with Stash {

  import DnsClient._
  import context.system

  val udp = IO(Udp)
  val tcp = IO(Tcp)

  private[internal] var inflightRequests: Map[Short, (ActorRef, Message)] = Map.empty

  lazy val tcpDnsClient: ActorRef = createTcpClient()

  override def preStart() = {
    udp ! Udp.Bind(self, new InetSocketAddress(InetAddress.getByAddress(Array.ofDim(4)), 0))
  }

  def receive: Receive = {
    case Udp.Bound(local) =>
      log.debug("Bound to UDP address [{}]", local)
      context.become(ready(sender()))
      unstashAll()
    case _: Question4 =>
      stash()
    case _: Question6 =>
      stash()
    case _: SrvQuestion =>
      stash()
  }

  private def message(name: String, id: Short, recordType: RecordType): Message = {
    Message(id, MessageFlags(), im.Seq(Question(name, recordType, RecordClass.IN)))
  }

  /**
   * Silent to allow map update syntax
   */
  @nowarn()
  def ready(socket: ActorRef): Receive = {
    case DropRequest(msg) =>
      inflightRequests.get(msg.id).foreach {
        case (_, orig) if Seq(msg.name) == orig.questions.map(_.name) =>
          log.debug("Dropping request [{}]", msg.id)
          inflightRequests -= msg.id
        case (_, orig) =>
          log.warning("Cannot drop inflight DNS request the question [{}] does not match [{}]",
            msg.name,
            orig.questions.map(_.name).mkString(","))
      }

    case Question4(id, name) =>
      log.debug("Resolving [{}] (A)", name)
      val msg = message(name, id, RecordType.A)
      newInflightRequests(msg, sender()) {
        log.debug("Message [{}] to [{}]: [{}]", id, ns, msg)
        socket ! Udp.Send(msg.write(), ns)
      }

    case Question6(id, name) =>
      log.debug("Resolving [{}] (AAAA)", name)
      val msg = message(name, id, RecordType.AAAA)
      newInflightRequests(msg, sender()) {
        log.debug("Message [{}] to [{}]: [{}]", id, ns, msg)
        socket ! Udp.Send(msg.write(), ns)
      }

    case SrvQuestion(id, name) =>
      log.debug("Resolving [{}] (SRV)", name)
      val msg = message(name, id, RecordType.SRV)
      newInflightRequests(msg, sender()) {
        log.debug("Message [{}] to [{}]: [{}]", id, ns, msg)
        socket ! Udp.Send(msg.write(), ns)
      }

    case Udp.CommandFailed(cmd) =>
      log.debug("Command failed [{}]", cmd)
      cmd match {
        case send: Udp.Send =>
          // best effort, don't throw
          Try {
            val msg = Message.parse(send.payload)
            inflightRequests.get(msg.id).foreach {
              case (s, orig) if isSameQuestion(msg.questions, orig.questions) =>
                s ! Failure(new RuntimeException("Send failed to nameserver"))
                inflightRequests -= msg.id
              case (_, orig) =>
                log.warning("Cannot command failed question [{}] does not match [{}]",
                  msg.questions.mkString(","),
                  orig.questions.mkString(","))
            }
          }
        case _ =>
          log.warning("Dns client failed to send {}", cmd)
      }
    case Udp.Received(data, remote) =>
      log.debug("Received message from [{}]: [{}]", remote, data)
      val msg = Message.parse(data)
      log.debug("Decoded UDP DNS response [{}]", msg)

      if (msg.flags.isTruncated) {
        log.debug("DNS response truncated, falling back to TCP")
        inflightRequests.get(msg.id) match {
          case Some((_, msg)) =>
            tcpDnsClient ! msg
          case _ =>
            log.debug("Client for id {} not found. Discarding unsuccessful response.", msg.id)
        }
      } else {
        inflightRequests.get(msg.id) match {
          case Some((_, orig)) if !isSameQuestion(msg.questions, orig.questions) =>
            log.warning(
              "Unexpected DNS response id {} question [{}] does not match question asked [{}]",
              msg.id,
              msg.questions.mkString(","),
              orig.questions.mkString(","))
          case Some((_, orig)) =>
            log.warning("DNS response id {} question [{}] question asked [{}]",
              msg.id,
              msg.questions.mkString(","),
              orig.questions.mkString(","))
            val (recs, additionalRecs) =
              if (msg.flags.responseCode == ResponseCode.SUCCESS) (msg.answerRecs, msg.additionalRecs) else (Nil, Nil)
            self ! Answer(msg.id, recs, additionalRecs)
          case None =>
            log.warning("Unexpected DNS response invalid id {}", msg.id)
        }
      }
    case response: Answer =>
      inflightRequests.get(response.id) match {
        case Some((reply, _)) =>
          reply ! response
          inflightRequests -= response.id
        case None =>
          log.debug("Client for id {} not found. Discarding response.", response.id)
      }
    case Udp.Unbind  => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }

  private def newInflightRequests(msg: Message, theSender: ActorRef)(func: => Unit): Unit = {
    if (!inflightRequests.contains(msg.id)) {
      inflightRequests += (msg.id -> (theSender -> msg))
      func
    } else {
      log.warning("Received duplicate message [{}] with id [{}]", msg, msg.id)
      theSender ! DuplicateId(msg.id)
    }
  }

  private def isSameQuestion(q1s: Seq[Question], q2s: Seq[Question]): Boolean = {
    @tailrec
    def impl(q1s: List[Question], q2s: List[Question]): Boolean = {
      (q1s, q2s) match {
        case (Nil, Nil)           => true
        case (h1 :: t1, h2 :: t2) => h1.isSame(h2) && impl(t1, t2)
        case _                    => false
      }
    }

    impl(q1s.sortBy(_.name).toList, q2s.sortBy(_.name).toList)
  }

  def createTcpClient() = {
    context.actorOf(
      BackoffSupervisor.props(
        BackoffOpts.onFailure(
          Props(classOf[TcpDnsClient], tcp, ns, self),
          childName = "tcpDnsClient",
          minBackoff = 10.millis,
          maxBackoff = 20.seconds,
          randomFactor = 0.1)),
      "tcpDnsClientSupervisor")
  }
}
