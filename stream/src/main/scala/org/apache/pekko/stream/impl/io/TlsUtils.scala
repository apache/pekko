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

import javax.net.ssl.{ SSLEngine, SSLParameters }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.TLSClientAuth
import pekko.stream.TLSProtocol.NegotiateNewSession

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object TlsUtils {
  def applySessionParameters(engine: SSLEngine, sessionParameters: NegotiateNewSession): Unit = {
    sessionParameters.enabledCipherSuites.foreach(cs => engine.setEnabledCipherSuites(cs.toArray))
    sessionParameters.enabledProtocols.foreach(p => engine.setEnabledProtocols(p.toArray))

    sessionParameters.sslParameters.foreach(engine.setSSLParameters)

    sessionParameters.clientAuth match {
      case Some(TLSClientAuth.None) => engine.setNeedClientAuth(false)
      case Some(TLSClientAuth.Want) => engine.setWantClientAuth(true)
      case Some(TLSClientAuth.Need) => engine.setNeedClientAuth(true)
      case _                        => // do nothing
    }
  }

  def cloneParameters(old: SSLParameters): SSLParameters = {
    val newParameters = new SSLParameters()
    newParameters.setAlgorithmConstraints(old.getAlgorithmConstraints)
    newParameters.setCipherSuites(old.getCipherSuites)
    newParameters.setEndpointIdentificationAlgorithm(old.getEndpointIdentificationAlgorithm)
    newParameters.setNeedClientAuth(old.getNeedClientAuth)
    newParameters.setProtocols(old.getProtocols)
    newParameters.setServerNames(old.getServerNames)
    newParameters.setSNIMatchers(old.getSNIMatchers)
    newParameters.setUseCipherSuitesOrder(old.getUseCipherSuitesOrder)
    newParameters.setWantClientAuth(old.getWantClientAuth)
    newParameters
  }
}
