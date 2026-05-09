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

package org.apache.pekko.io.dns.internal

import java.net.{ Inet4Address, Inet6Address, InetAddress, InetSocketAddress }

import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.{ ExecutionContextExecutor, Future, Promise }
import scala.concurrent.ExecutionContext.parasitic
import scala.util.{ Failure, Success, Try }
import scala.util.control.NonFatal

import org.apache.pekko
import pekko.actor.{ Actor, ActorLogging, ActorRef, ActorRefFactory, NoSerializationVerificationNeeded, Props, Status }
import pekko.annotation.InternalApi
import pekko.io.SimpleDnsCache
import pekko.io.dns._
import pekko.io.dns.CachePolicy.{ Never, Ttl }
import pekko.io.dns.DnsProtocol.{ Ip, RequestType, Srv }
import pekko.io.dns.internal.DnsClient._
import pekko.pattern.{ ask, pipe }
import pekko.pattern.AskTimeoutException
import pekko.util.{ Helpers, Timeout }
import pekko.util.PrettyDuration._

/**
 * INTERNAL API
 */
@InternalApi
private[io] final class AsyncDnsResolver(
    settings: DnsSettings,
    cache: SimpleDnsCache,
    clientFactory: (ActorRefFactory, List[InetSocketAddress]) => List[ActorRef],
    idGenerator: IdGenerator)
    extends Actor
    with ActorLogging {

  def this(
      settings: DnsSettings,
      cache: SimpleDnsCache,
      clientFactory: (ActorRefFactory, List[InetSocketAddress]) => List[ActorRef]) =
    this(settings, cache, clientFactory, IdGenerator(settings.IdGeneratorPolicy))

  import AsyncDnsResolver._

  // avoid ever looking up localhost by pre-populating cache
  {
    val loopback = InetAddress.getLoopbackAddress
    val (ipv4Address, ipv6Address) = loopback match {
      case ipv6: Inet6Address => (InetAddress.getByName("127.0.0.1"), ipv6)
      case ipv4: Inet4Address => (ipv4, InetAddress.getByName("::1"))
      case unknown            => throw new IllegalArgumentException(s"Loopback address was [$unknown]")
    }
    cache.put(
      "localhost" -> Ip(),
      DnsProtocol.Resolved("localhost", ARecord("localhost", Ttl.effectivelyForever, loopback) :: Nil),
      Ttl.effectivelyForever)
    cache.put(
      "localhost" -> Ip(ipv6 = false, ipv4 = true),
      DnsProtocol.Resolved("localhost", ARecord("localhost", Ttl.effectivelyForever, ipv4Address) :: Nil),
      Ttl.effectivelyForever)
    cache.put(
      "localhost" -> Ip(ipv6 = true, ipv4 = false),
      DnsProtocol.Resolved("localhost", ARecord("localhost", Ttl.effectivelyForever, ipv6Address) :: Nil),
      Ttl.effectivelyForever)

  }

  val nameServers = settings.NameServers

  val positiveCachePolicy = settings.PositiveCachePolicy
  val negativeCachePolicy = settings.NegativeCachePolicy
  log.debug(
    "Using name servers [{}] and search domains [{}] with ndots={}",
    nameServers,
    settings.SearchDomains,
    settings.NDots)

  private val resolvers: List[ActorRef] = clientFactory(context, nameServers)
  private val requestIdInjector: ActorRef = context.actorOf(RequestIdInjector.props(idGenerator), "requestIdInjector")

  // tracks in-flight resolutions by (name, requestType) -> list of senders waiting for the result
  private var inFlight: Map[(String, RequestType), List[ActorRef]] = Map.empty

  // only supports DnsProtocol, not the deprecated Dns protocol
  // AsyncDnsManager converts between the protocols to support the deprecated protocol
  override def receive: Receive = {
    case DnsProtocol.Resolve(name, mode) =>
      cache.get((name, mode)) match {
        case Some(resolved) =>
          log.debug("{} cached {}", mode, resolved)
          sender() ! resolved
        case None =>
          // check if we're being asked to resolve an IP address, in which case no need to do anything async
          if (isInetAddress(name)) {
            Try {
              InetAddress.getByName(name) match { // only a validity check, doesn't do blocking I/O
                case ipv4: Inet4Address => ARecord(name, Ttl.effectivelyForever, ipv4)
                case ipv6: Inet6Address => AAAARecord(name, Ttl.effectivelyForever, ipv6)
                case unexpected         => throw new IllegalArgumentException(s"Unexpected address: $unexpected")
              }
            }.fold(
              ex => { sender() ! Status.Failure(ex) },
              record => {
                val resolved = DnsProtocol.Resolved(name, record :: Nil)
                cache.put(name -> mode, resolved, record.ttl)
                sender() ! resolved
              })
          } else if (inFlight.contains((name, mode))) {
            // there's already a resolution in progress for this (name, mode); add to waiters
            inFlight.get((name, mode)).foreach { waiters =>
              inFlight = inFlight.updated((name, mode), sender() :: waiters)
            }
          } else if (resolvers.isEmpty) {
            sender() ! Status.Failure(failToResolve(name, nameServers))
          } else {
            // spawn an actor to manage this resolution (apply search names, failover to other resolvers, etc.)
            inFlight = inFlight.updated((name, mode), List(sender()))
            context.actorOf(
              DnsResolutionActor.props(settings, requestIdInjector, name, mode, self, resolvers))
          }
      }

    case ResolutionAnswer(name, mode, result) =>
      inFlight.get((name, mode)).foreach { waiters =>
        result match {
          case Success(resolved) =>
            if (resolved.records.nonEmpty) {
              val minTtl = (positiveCachePolicy +: resolved.records.map(_.ttl)).min
              cache.put((name, mode), resolved, minTtl)
            } else if (negativeCachePolicy != Never)
              cache.put((name, mode), resolved, negativeCachePolicy)
            log.debug("{} resolved {}", mode, resolved)
            waiters.foreach(_ ! resolved)
          case Failure(ex) =>
            waiters.foreach(_ ! Status.Failure(ex))
        }
      }
      inFlight -= ((name, mode))
  }

}

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] object AsyncDnsResolver {

  private val ipv4Address =
    """^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$""".r

  private val ipv6Address =
    """^\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*$"""
      .r

  private[pekko] def isIpv4Address(name: String): Boolean =
    ipv4Address.findAllMatchIn(name).nonEmpty

  private[pekko] def isIpv6Address(name: String): Boolean =
    ipv6Address.findAllMatchIn(name).nonEmpty

  private def isInetAddress(name: String): Boolean =
    isIpv4Address(name) || isIpv6Address(name)

  private val Empty =
    Future.successful(Answer(-1, immutable.Seq.empty[ResourceRecord], immutable.Seq.empty[ResourceRecord]))

  private[pekko] def failToResolve(name: String, nameServers: List[InetSocketAddress]): ResolveFailedException =
    ResolveFailedException(s"Failed to resolve $name with nameservers $nameServers")

  case class ResolveFailedException(msg: String) extends Exception(msg)

  private final case class ResolutionAnswer(
      name: String,
      mode: RequestType,
      result: Try[DnsProtocol.Resolved])

  private sealed trait DnsQuestionPreInjection extends NoSerializationVerificationNeeded {
    def requestId: Long
    def resolver: ActorRef
    def timeout: Timeout
    def withId(id: Short): DnsQuestion
  }

  private final case class Question4PreInjection(requestId: Long, resolver: ActorRef, name: String, timeout: Timeout)
      extends DnsQuestionPreInjection {
    override def withId(id: Short): DnsQuestion = Question4(id, name)
  }

  private final case class Question6PreInjection(requestId: Long, resolver: ActorRef, name: String, timeout: Timeout)
      extends DnsQuestionPreInjection {
    override def withId(id: Short): DnsQuestion = Question6(id, name)
  }

  private final case class SrvQuestionPreInjection(requestId: Long, resolver: ActorRef, name: String, timeout: Timeout)
      extends DnsQuestionPreInjection {
    override def withId(id: Short): DnsQuestion = SrvQuestion(id, name)
  }

  private final case class DnsQuestionAnswer(
      replyTo: ActorRef,
      request: DnsQuestionPreInjection,
      question: DnsQuestion,
      result: Try[Any])
      extends NoSerializationVerificationNeeded

  private final case class InjectedDnsQuestionAnswer(requestId: Long, result: Try[Answer])
      extends NoSerializationVerificationNeeded

  private final case class DidntDrop(id: Short) extends NoSerializationVerificationNeeded

  private object RequestIdInjector {
    private val MaxIdGenerationAttempts = 1 << 16

    def props(idGenerator: IdGenerator): Props = Props(new RequestIdInjector(idGenerator))
  }

  private class RequestIdInjector(idGenerator: IdGenerator) extends Actor with ActorLogging {
    import RequestIdInjector._

    private implicit val ec: ExecutionContextExecutor = context.dispatcher
    private var activeRequestIds = Set.empty[Short]

    override def receive: Receive = {
      case question: DnsQuestionPreInjection =>
        sendQuestionWithNewId(sender(), question)

      case DnsQuestionAnswer(replyTo, request, question, Success(result: Answer)) =>
        activeRequestIds -= question.id
        replyTo ! InjectedDnsQuestionAnswer(request.requestId, Success(result))

      case DnsQuestionAnswer(replyTo, request, question, Success(DuplicateId(_))) =>
        sendQuestionWithNewId(replyTo, request)

      case DnsQuestionAnswer(replyTo, request, question, Failure(t)) =>
        replyTo ! InjectedDnsQuestionAnswer(request.requestId, Failure(t))
        dropQuestion(request.resolver, request.timeout, question)

      case DnsQuestionAnswer(replyTo, request, question, Success(a)) =>
        replyTo ! InjectedDnsQuestionAnswer(
          request.requestId,
          Failure(
            new IllegalArgumentException("Unexpected response " + a.toString + " of type " + a.getClass.toString)))
        dropQuestion(request.resolver, request.timeout, question)

      case Dropped(id) =>
        activeRequestIds -= id

      case DidntDrop(id) =>
        log.warning("DNS request id [{}] could not be confirmed dropped, keeping it reserved", id)
    }

    private def sendQuestionWithNewId(replyTo: ActorRef, request: DnsQuestionPreInjection): Unit =
      nextAvailableRequestId() match {
        case Success(id) =>
          sendQuestion(replyTo, request, request.withId(id))
        case Failure(t) =>
          replyTo ! InjectedDnsQuestionAnswer(request.requestId, Failure(t))
      }

    private def sendQuestion(replyTo: ActorRef, request: DnsQuestionPreInjection, question: DnsQuestion): Unit = {
      activeRequestIds += question.id
      implicit val askTimeout: Timeout = request.timeout
      (request.resolver ? question).onComplete { result =>
        self ! DnsQuestionAnswer(replyTo, request, question, result)
      }
    }

    @tailrec
    private def nextAvailableRequestId(attemptsLeft: Int = MaxIdGenerationAttempts): Try[Short] =
      if (attemptsLeft == 0) {
        Failure(new IllegalStateException("No non-active DNS request id could be generated"))
      } else {
        Try(idGenerator.nextId()) match {
          case Failure(t)  => Failure(t)
          case Success(id) =>
            if (activeRequestIds.contains(id)) nextAvailableRequestId(attemptsLeft - 1)
            else Success(id)
        }
      }

    private def dropQuestion(resolver: ActorRef, timeout: Timeout, question: DnsQuestion): Unit = {
      implicit val askTimeout: Timeout = timeout
      (resolver ? DropRequest(question)).map {
        case dropped: Dropped => dropped
        case other            =>
          log.warning("Unexpected response [{}] when dropping DNS request id [{}]", other, question.id)
          DidntDrop(question.id)
      }.recover {
        case NonFatal(t) =>
          log.warning("Drop request for DNS request id [{}] failed: {}", question.id, t.getMessage)
          DidntDrop(question.id)
      }.foreach(self ! _)
    }
  }

  private object DnsResolutionActor {
    def props(
        settings: DnsSettings,
        requestIdInjector: ActorRef,
        name: String,
        mode: RequestType,
        responseActor: ActorRef,
        resolvers: List[ActorRef]): Props =
      Props(new DnsResolutionActor(settings, requestIdInjector, name, mode, responseActor, resolvers))
  }

  // Per-request actor that manages DNS resolution: applies search domains, fails over to other resolvers.
  // Reports the final result back to `responseActor` (the AsyncDnsResolver) via `ResolutionAnswer`.
  private class DnsResolutionActor(
      settings: DnsSettings,
      requestIdInjector: ActorRef,
      name: String,
      mode: RequestType,
      responseActor: ActorRef,
      resolvers: List[ActorRef])
      extends Actor
      with ActorLogging {

    private implicit val timeout: Timeout = Timeout(settings.ResolveTimeout)
    private implicit val ec: ExecutionContextExecutor = context.dispatcher
    private var nextRequestId = 0L
    private var pendingQuestions = Map.empty[Long, Promise[Answer]]

    private def failToResolve(): Unit = {
      responseActor ! ResolutionAnswer(name, mode, Failure(AsyncDnsResolver.failToResolve(name, settings.NameServers)))
      context.stop(self)
    }

    // Not strictly necessary, since our parent checks this before spawning, but belt-and-suspenders
    if (resolvers.isEmpty) {
      failToResolve() // (vacuously) exhausted the resolvers
      // fail fast
      throw new IllegalArgumentException("resolvers should not be empty")
    }

    // the fully-qualified domain names (FQDNs), in the order we want to attempt for any given resolver
    private val namesToResolve = {
      val nameWithSearch = settings.SearchDomains.map(sd => s"${name}.${sd}")

      // ndots is a heuristic used to attempt to work out if `name` is an FQDN or a name relative to a search
      // name. The goal is to not incur the cost of a lookup that's unlikely to resolve because we need to
      // relativize it. If the name being searched contains less than ndots dots, then we look it up last,
      // rather than first.
      if (name.count(_ == '.') >= settings.NDots) {
        // assume fully-qualified, so try `name` first
        (name :: nameWithSearch).map(Helpers.toRootLowerCase)
      } else {
        // assume relative, so try `name` last
        (nameWithSearch :+ name).map(Helpers.toRootLowerCase)
      }
    }

    // Initial receive is empty; startResolution immediately calls context.become to switch to activelyResolving
    override def receive: Receive = PartialFunction.empty
    // safe, already verified that resolvers is non-empty
    startResolution(namesToResolve, resolvers.head, resolvers.tail)

    private def questionAnswer: Receive = {
      case InjectedDnsQuestionAnswer(requestId, result) =>
        pendingQuestions.get(requestId).foreach(_.complete(result))
        pendingQuestions -= requestId
    }

    private def activelyResolving(
        searchName: String,
        resolver: ActorRef,
        nextNamesToTry: List[String],
        nextResolversToTry: List[ActorRef]): Receive = questionAnswer.orElse {
      case resolved: DnsProtocol.Resolved =>
        if (resolved.records.isEmpty) {
          if (nextNamesToTry.nonEmpty) startResolution(nextNamesToTry, resolver, nextResolversToTry)
          else handleResolved(resolved) // empty but successful response
        } else {
          handleResolved(resolved)
        }

      case Status.Failure(ex) =>
        ex match {
          case _: AskTimeoutException =>
            log.info("Resolve of {} timed out after {}. Trying next name server", searchName, timeout.duration.pretty)
          case _ =>
            log.info("Resolve of {} failed. Trying next name server. Exception: {}", name, ex.getMessage)
        }

        // failed, move on to next name server
        if (nextResolversToTry.nonEmpty)
          startResolution(namesToResolve, nextResolversToTry.head, nextResolversToTry.tail)
        else failToResolve() // exhausted the resolvers
    }

    private def startResolution(
        namesToTry: List[String],
        resolverToUse: ActorRef,
        fallbackResolvers: List[ActorRef]): Unit = {
      if (namesToTry.nonEmpty) {
        val searchName = namesToTry.head

        log.debug("Attempting to resolve {} with {}", searchName, resolverToUse)

        resolvedFut(searchName, resolverToUse).pipeTo(self)

        context.become(activelyResolving(searchName, resolverToUse, namesToTry.tail, fallbackResolvers))
      } else {
        // shouldn't happen
        log.warning("startResolution called with empty list of namesToTry")
        failToResolve()
      }
    }

    private def handleResolved(resolved: DnsProtocol.Resolved): Unit = {
      responseActor ! ResolutionAnswer(name, mode, Success(resolved))
      context.stop(self)
    }

    private def sendQuestion(createQuestion: Long => DnsQuestionPreInjection): Future[Answer] = {
      nextRequestId += 1
      val requestId = nextRequestId
      val promise = Promise[Answer]()
      pendingQuestions += requestId -> promise
      requestIdInjector.tell(createQuestion(requestId), self)
      promise.future
    }

    private def resolvedFut(searchName: String, resolver: ActorRef): Future[DnsProtocol.Resolved] =
      mode match {
        case Ip(ipv4, ipv6) =>
          val ipv4Recs =
            if (ipv4) sendQuestion(Question4PreInjection(_, resolver, searchName, timeout))
            else Empty

          val ipv6Recs =
            if (ipv6) sendQuestion(Question6PreInjection(_, resolver, searchName, timeout))
            else Empty

          ipv4Recs.flatMap { v4 =>
            ipv6Recs.map { v6 =>
              DnsProtocol.Resolved(searchName, v4.rrs ++ v6.rrs, v4.additionalRecs ++ v6.additionalRecs)
            }(parasitic)
          }(parasitic)

        case Srv =>
          sendQuestion(SrvQuestionPreInjection(_, resolver, searchName, timeout)).map { answer =>
            DnsProtocol.Resolved(searchName, answer.rrs, answer.additionalRecs)
          }(parasitic)
      }
  }
}
