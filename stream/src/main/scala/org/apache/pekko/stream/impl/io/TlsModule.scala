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

package org.apache.pekko.stream.impl.io

import javax.net.ssl.{ SSLEngine, SSLSession }

import scala.util.Try

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorSystem
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.TLSProtocol._
import pekko.stream.impl.{ TlsModuleIslandTag, TraversalBuilder }
import pekko.stream.impl.StreamLayout.AtomicModule
import pekko.util.ByteString

/**
 * INTERNAL API.
 */
@InternalApi private[stream] final case class TlsModule(
    plainIn: Inlet[SslTlsOutbound],
    plainOut: Outlet[SslTlsInbound],
    cipherIn: Inlet[ByteString],
    cipherOut: Outlet[ByteString],
    shape: BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound],
    attributes: Attributes,
    createSSLEngine: ActorSystem => SSLEngine, // ActorSystem is only needed to support the PekkoSSLConfig legacy, see #21753
    verifySession: (ActorSystem, SSLSession) => Try[Unit], // ActorSystem is only needed to support the PekkoSSLConfig legacy, see #21753
    closing: TLSClosing)
    extends AtomicModule[BidiShape[SslTlsOutbound, ByteString, ByteString, SslTlsInbound], NotUsed] {

  override def withAttributes(att: Attributes): TlsModule = copy(attributes = att)

  override def toString: String = f"TlsModule($closing) [${System.identityHashCode(this)}%08x]"

  override private[stream] def traversalBuilder =
    TraversalBuilder.atomic(this, attributes).makeIsland(TlsModuleIslandTag)
}

/**
 * INTERNAL API.
 */
@InternalApi private[stream] object TlsModule {
  def apply(
      attributes: Attributes,
      createSSLEngine: ActorSystem => SSLEngine, // ActorSystem is only needed to support the PekkoSSLConfig legacy, see #21753
      verifySession: (ActorSystem, SSLSession) => Try[Unit], // ActorSystem is only needed to support the PekkoSSLConfig legacy, see #21753
      closing: TLSClosing): TlsModule = {
    val name = attributes.nameOrDefault(s"StreamTls()")
    val cipherIn = Inlet[ByteString](s"$name.cipherIn")
    val cipherOut = Outlet[ByteString](s"$name.cipherOut")
    val plainIn = Inlet[SslTlsOutbound](s"$name.transportIn")
    val plainOut = Outlet[SslTlsInbound](s"$name.transportOut")
    val shape = new BidiShape(plainIn, cipherOut, cipherIn, plainOut)
    TlsModule(plainIn, plainOut, cipherIn, cipherOut, shape, attributes, createSSLEngine, verifySession, closing)
  }
}
