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

package org.apache.pekko.remote.transport.netty4

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory

import scala.util.Try

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.event.MarkerLoggingAdapter
import pekko.remote.RemoteTransportException
import pekko.remote.artery.tcp.SecureRandomFactory
import pekko.stream.TLSRole

@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
trait SSLEngineProvider {

  def createServerSSLEngine(): SSLEngine

  def createClientSSLEngine(): SSLEngine

}

/**
 * Config in pekko.remote.classic.netty.ssl.security
 *
 * Subclass may override protected methods to replace certain parts, such as key and trust manager.
 */
@deprecated("Classic remoting is deprecated, use Artery", "Akka 2.6.0")
class ConfigSSLEngineProvider(protected val log: MarkerLoggingAdapter, private val settings: SSLSettings)
    extends SSLEngineProvider {

  def this(system: ActorSystem) =
    this(
      Logging.withMarker(system, classOf[ConfigSSLEngineProvider].getName),
      new SSLSettings(system.settings.config.getConfig("pekko.remote.classic.netty.ssl.security")))

  import settings._

  private lazy val sslContext: SSLContext = {
    try {
      val rng = createSecureRandom()
      val ctx = SSLContext.getInstance(SSLProtocol)
      ctx.init(keyManagers, trustManagers, rng)
      ctx
    } catch {
      case e: FileNotFoundException =>
        throw new RemoteTransportException(
          "Server SSL connection could not be established because key store could not be loaded",
          e)
      case e: IOException =>
        throw new RemoteTransportException("Server SSL connection could not be established because: " + e.getMessage, e)
      case e: GeneralSecurityException =>
        throw new RemoteTransportException(
          "Server SSL connection could not be established because SSL context could not be constructed",
          e)
    }
  }

  /**
   * Subclass may override to customize loading of `KeyStore`
   */
  protected def loadKeystore(filename: String, password: String): KeyStore = {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    val fin = Files.newInputStream(Paths.get(filename))
    try keyStore.load(fin, password.toCharArray)
    finally Try(fin.close())
    keyStore
  }

  /**
   * Subclass may override to customize `KeyManager`
   */
  protected def keyManagers: Array[KeyManager] = {
    val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    factory.init(loadKeystore(SSLKeyStore, SSLKeyStorePassword), SSLKeyPassword.toCharArray)
    factory.getKeyManagers
  }

  /**
   * Subclass may override to customize `TrustManager`
   */
  protected def trustManagers: Array[TrustManager] = {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(loadKeystore(SSLTrustStore, SSLTrustStorePassword))
    trustManagerFactory.getTrustManagers
  }

  def createSecureRandom(): SecureRandom =
    SecureRandomFactory.createSecureRandom(SSLRandomNumberGenerator, log)

  override def createServerSSLEngine(): SSLEngine =
    createSSLEngine(pekko.stream.Server)

  override def createClientSSLEngine(): SSLEngine =
    createSSLEngine(pekko.stream.Client)

  private def createSSLEngine(role: TLSRole): SSLEngine = {
    createSSLEngine(sslContext, role)
  }

  private def createSSLEngine(sslContext: SSLContext, role: TLSRole): SSLEngine = {

    val engine = sslContext.createSSLEngine()

    engine.setUseClientMode(role == pekko.stream.Client)
    engine.setEnabledCipherSuites(SSLEnabledAlgorithms.toArray)
    engine.setEnabledProtocols(Array(SSLProtocol))

    if ((role != pekko.stream.Client) && SSLRequireMutualAuthentication)
      engine.setNeedClientAuth(true)

    engine
  }

}
