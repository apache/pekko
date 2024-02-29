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

package org.apache.pekko.pki.pem

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.PrivateKey

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DERPrivateKeyLoaderSpec extends AnyWordSpec with Matchers with EitherValues {

  "The DER Private Key loader" should {
    "decode the same key in PKCS#1 and PKCS#8 formats" in {
      val pkcs1 = load("pkcs1.pem")
      val pkcs8 = load("pkcs8.pem")
      pkcs1 should ===(pkcs8)
    }

    "parse multi primes" in {
      load("multi-prime-pkcs1.pem")
      // Not much we can verify here - I actually think the default JDK security implementation ignores the extra
      // primes, and it fails to parse a multi-prime PKCS#8 key.
    }

    "fail on unsupported PEM contents (Certificates are not private keys)" in {
      assertThrows[PEMLoadingException] {
        load("certificate.pem")
      }
    }

  }

  private def load(resource: String): PrivateKey = {
    val derData: PEMDecoder.DERData = loadDerData(resource)
    DERPrivateKeyLoader.load(derData)
  }

  private def loadDerData(resource: String) = {
    val resourceUrl = getClass.getClassLoader.getResource(resource)
    resourceUrl.getProtocol should ===("file")
    val path = new File(resourceUrl.toURI).toPath
    val bytes = Files.readAllBytes(path)
    val str = new String(bytes, StandardCharsets.UTF_8)
    val derData = PEMDecoder.decode(str)
    derData
  }

}
