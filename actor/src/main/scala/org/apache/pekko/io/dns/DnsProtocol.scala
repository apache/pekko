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

package org.apache.pekko.io.dns

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util

import scala.collection.{ immutable => im }

import org.apache.pekko
import pekko.actor.NoSerializationVerificationNeeded
import pekko.io.IpVersionSelector
import pekko.routing.ConsistentHashingRouter.ConsistentHashable
import scala.jdk.CollectionConverters._

/**
 * Supersedes [[pekko.io.Dns]] protocol.
 *
 * Note that one MUST configure `pekko.io.dns.resolver = async-dns` to make use of this protocol and resolver.
 *
 * Allows for more detailed lookups, by specifying which records should be checked,
 * and responses can more information than plain IP addresses (e.g. ports for SRV records).
 */
object DnsProtocol {

  sealed trait RequestType
  final case class Ip(ipv4: Boolean = true, ipv6: Boolean = true) extends RequestType
  case object Srv extends RequestType

  /**
   * Java API
   */
  def ipRequestType(ipv4: Boolean, ipv6: Boolean): RequestType = Ip(ipv4, ipv6)

  /**
   * Java API
   */
  def ipRequestType(): RequestType = Ip(ipv4 = true, ipv6 = true)

  /**
   * Java API
   */
  def srvRequestType(): RequestType = Srv

  /**
   * Sending this to the [[internal.AsyncDnsManager]] will either lead to a [[Resolved]] or a [[pekko.actor.Status.Failure]] response.
   * If request type are both, both resolutions must succeed or the response is a failure.
   */
  final case class Resolve(name: String, requestType: RequestType) extends ConsistentHashable {
    override def consistentHashKey: Any = name
  }

  object Resolve {
    def apply(name: String): Resolve = Resolve(name, Ip())
  }

  /**
   * Java API
   */
  def resolve(name: String): Resolve = Resolve(name, Ip())

  /**
   * Java API
   */
  def resolve(name: String, requestType: RequestType): Resolve = Resolve(name, requestType)

  /**
   * @param name of the record
   * @param records resource records for the query
   * @param additionalRecords records that relate to the query but are not strictly answers
   */
  final case class Resolved(name: String, records: im.Seq[ResourceRecord], additionalRecords: im.Seq[ResourceRecord])
      extends NoSerializationVerificationNeeded {

    /**
     * Java API
     *
     * Records for the query
     */
    def getRecords(): util.List[ResourceRecord] = records.asJava

    /**
     * Java API
     *
     * Records that relate to the query but are not strickly answers e.g. A records for the records returned for an SRV query.
     */
    def getAdditionalRecords(): util.List[ResourceRecord] = additionalRecords.asJava

    private val _address: Option[InetAddress] = {
      val ipv4: Option[Inet4Address] = records.collectFirst { case ARecord(_, _, ip: Inet4Address) => ip }
      val ipv6: Option[Inet6Address] = records.collectFirst { case AAAARecord(_, _, ip) => ip }
      IpVersionSelector.getInetAddress(ipv4, ipv6)
    }

    /**
     * Return the host, taking into account the "java.net.preferIPv6Addresses" system property.
     *
     * @throws java.net.UnknownHostException
     */
    @throws[UnknownHostException]
    def address(): InetAddress = _address match {
      case None            => throw new UnknownHostException(name)
      case Some(ipAddress) => ipAddress
    }
  }

  object Resolved {

    def apply(name: String, records: im.Seq[ResourceRecord]): Resolved =
      new Resolved(name, records, Nil)
  }

}
