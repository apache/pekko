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
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.{ KeyStore, PrivateKey }
import java.security.cert.{ Certificate, CertificateFactory, X509Certificate }
import javax.net.ssl.{ KeyManager, KeyManagerFactory, TrustManager, TrustManagerFactory }
import javax.security.auth.x500.X500Principal

import scala.annotation.tailrec
import scala.concurrent.blocking
import scala.jdk.CollectionConverters._

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.pki.pem.{ DERPrivateKeyLoader, PEMDecoder }

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
      cacerts: Seq[Certificate],
      keystorePassword: String = "changeit"): Array[KeyManager] = {
    val keyStore = KeyStore.getInstance("JKS")
    keyStore.load(null)

    val passwordChars = keystorePassword.toCharArray
    keyStore.setCertificateEntry("cert", cert)
    cacerts.zipWithIndex.foreach { case (cacert, idx) => keyStore.setCertificateEntry(caCertAlias(idx), cacert) }
    keyStore.setKeyEntry("private-key", privateKey, passwordChars, buildCertificateChain(cert, cacerts))

    val kmf =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    kmf.init(keyStore, passwordChars)
    val keyManagers = kmf.getKeyManagers
    keyManagers
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def buildTrustManagers(cacerts: Seq[Certificate]): Array[TrustManager] = {
    val trustStore = KeyStore.getInstance("JKS")
    trustStore.load(null)
    cacerts.zipWithIndex.foreach { case (cacert, idx) => trustStore.setCertificateEntry(caCertAlias(idx), cacert) }

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
    val pemData = Files.readString(new File(filename).toPath, StandardCharsets.UTF_8)
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

  /**
   * Loads every certificate found in the given PEM file. A file may bundle more than one
   * certificate, e.g. a root CA together with the intermediates it delegates to, or several
   * roots while a CA is being rotated.
   *
   * INTERNAL API
   */
  @InternalApi
  private[ssl] def loadCertificates(filename: String): Seq[Certificate] = blocking {
    val stream = Files.newInputStream(new File(filename).toPath)
    try certFactory.generateCertificates(stream).asScala.toList
    finally stream.close()
  }

  private def caCertAlias(idx: Int): String = if (idx == 0) "cacert" else s"cacert-$idx"

  /**
   * Builds the certificate chain to present during the handshake: `cert` followed by the
   * certificates from `cacerts` that actually issued it, ordered from the issuer of `cert` up
   * to the root. Certificates in `cacerts` that are not part of that chain (an unrelated root
   * kept around for a CA rotation, say) are trusted but not sent to the peer.
   *
   * Falls back to sending all of `cacerts` when none of them matches the issuer of `cert`, which
   * keeps the behaviour of a single, non-matching CA certificate unchanged.
   */
  private def buildCertificateChain(cert: X509Certificate, cacerts: Seq[Certificate]): Array[Certificate] = {
    val bySubject: Map[X500Principal, X509Certificate] =
      cacerts.collect { case x509: X509Certificate => x509.getSubjectX500Principal -> x509 }.toMap

    @tailrec
    def issuersOf(current: X509Certificate, acc: List[Certificate]): List[Certificate] =
      bySubject.get(current.getIssuerX500Principal) match {
        // a self-signed certificate is its own issuer, so stop once the chain repeats itself
        case Some(issuer) if issuer != current && !acc.contains(issuer) => issuersOf(issuer, issuer :: acc)
        case _                                                          => acc
      }

    val issuers = issuersOf(cert, Nil).reverse
    if (issuers.isEmpty) (cert +: cacerts).toArray
    else (cert +: issuers).toArray
  }

}
