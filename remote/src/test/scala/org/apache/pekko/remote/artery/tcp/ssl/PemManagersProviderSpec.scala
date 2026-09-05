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

import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager

import scala.annotation.nowarn

import org.apache.pekko
import pekko.testkit.PekkoSpec.resourcePath

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

@nowarn("msg=deprecated")
class PemManagersProviderSpec extends AnyWordSpec with Matchers {

  "A PemManagersProvider" must {

    "load stores reading files setup in config (pekko-pki samples)" in {
      // These set of certificates are valid PEMs but are invalid for pekko-remote
      // use. Either the key length, certificate usage limitations (via the UsageKeyExtensions),
      // or the fact that the key's certificate is self-signed cause one of the following
      // errors: `certificate_unknown`, `certificate verify message signature error`/`bad_certificate`
      // during the SSLHandshake.
      withFiles("ssl/pem/pkcs1.pem", "ssl/pem/selfsigned-certificate.pem", "ssl/pem/selfsigned-certificate.pem") {
        (pk, cert, cacert) =>
          PemManagersProvider.buildKeyManagers(pk, cert, Seq(cacert)).length must be(1)
          PemManagersProvider.buildTrustManagers(Seq(cacert)).length must be(1)
          cert.getSubjectDN.getName must be("CN=0d207b68-9a20-4ee8-92cb-bf9699581cf8")
      }
    }

    "load stores reading files setup in config (keytool samples)" in {
      withFiles("ssl/node.example.com.pem", "ssl/node.example.com.crt", "ssl/exampleca.crt") { (pk, cert, cacert) =>
        PemManagersProvider.buildKeyManagers(pk, cert, Seq(cacert)).length must be(1)
        PemManagersProvider.buildTrustManagers(Seq(cacert)).length must be(1)
        cert.getSubjectDN.getName must be(
          "CN=node.example.com, OU=Example Org, O=Example Company, L=San Francisco, ST=California, C=US")
      }
    }

    "trust every certificate in a CA file bundling more than one" in {
      val cacerts = PemManagersProvider.loadCertificates(resourcePath("ssl/exampleca-bundle.crt"))
      cacerts.size must be(2)

      val trustManagers = PemManagersProvider.buildTrustManagers(cacerts)
      trustManagers.length must be(1)
      val acceptedIssuers = trustManagers.head.asInstanceOf[X509TrustManager].getAcceptedIssuers
      acceptedIssuers.map(_.getSubjectX500Principal).toSet must be(
        cacerts.map(_.asInstanceOf[X509Certificate].getSubjectX500Principal).toSet)
    }

    "only present the CA certificates that issued the node certificate" in {
      val pk = PemManagersProvider.loadPrivateKey(resourcePath("ssl/node.example.com.pem"))
      val cert =
        PemManagersProvider.loadCertificate(resourcePath("ssl/node.example.com.crt")).asInstanceOf[X509Certificate]
      val cacerts = PemManagersProvider.loadCertificates(resourcePath("ssl/exampleca-bundle.crt"))

      val keyManager = PemManagersProvider.buildKeyManagers(pk, cert, cacerts).head.asInstanceOf[X509KeyManager]
      val aliases = Option(keyManager.getClientAliases(pk.getAlgorithm, null)).getOrElse(Array.empty[String])
      aliases must not be empty

      // the unrelated, self-signed certificate in the bundle is trusted but not part of the chain
      val chain = keyManager.getCertificateChain(aliases.head)
      chain.length must be(2)
      chain(0) must be(cert)
      chain(1).asInstanceOf[X509Certificate].getSubjectX500Principal must be(
        cacerts.head.asInstanceOf[X509Certificate].getSubjectX500Principal)
    }

  }

  private def withFiles(keyFile: String, certFile: String, caCertFile: String)(
      block: (PrivateKey, X509Certificate, Certificate) => Unit) = {
    block(
      PemManagersProvider.loadPrivateKey(resourcePath(keyFile)),
      PemManagersProvider.loadCertificate(resourcePath(certFile)).asInstanceOf[X509Certificate],
      PemManagersProvider.loadCertificate(resourcePath(caCertFile)))
  }

}
