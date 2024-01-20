/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.tcp.ssl

import java.io.File
import java.nio.file.Files
import java.security.{ KeyStore, PrivateKey }
import java.security.cert.{ Certificate, CertificateFactory, X509Certificate }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.pki.pem.{ DERPrivateKeyLoader, PEMDecoder }

import javax.net.ssl.{ KeyManager, KeyManagerFactory, TrustManager, TrustManagerFactory }

import scala.concurrent.blocking

/**
 * INTERNAL API
 */
@InternalApi
private[ssl] object PemManagersProvider {

  /**
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def buildKeyManagers(
      privateKey: PrivateKey,
      cert: X509Certificate,
      cacert: Certificate): Array[KeyManager] = {
    val keyStore = KeyStore.getInstance("JKS")
    keyStore.load(null)

    keyStore.setCertificateEntry("cert", cert)
    keyStore.setCertificateEntry("cacert", cacert)
    keyStore.setKeyEntry("private-key", privateKey, "changeit".toCharArray, Array(cert, cacert))

    val kmf =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, "changeit".toCharArray)
    val keyManagers = kmf.getKeyManagers
    keyManagers
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def buildTrustManagers(cacert: Certificate): Array[TrustManager] = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null)
    trustStore.setCertificateEntry("cacert", cacert)

    val tmf =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    tmf.init(trustStore)
    tmf.getTrustManagers
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def loadPrivateKey(filename: String): PrivateKey = blocking {
    val pemData = Files.readString(new File(filename).toPath)
    DERPrivateKeyLoader.load(PEMDecoder.decode(pemData))
  }

  private val certFactory = CertificateFactory.getInstance("X.509")

  /**
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def loadCertificate(filename: String): Certificate = blocking {
    certFactory.generateCertificate(Files.newInputStream(new File(filename).toPath))
  }

}
