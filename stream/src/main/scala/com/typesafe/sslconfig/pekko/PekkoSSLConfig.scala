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

package com.typesafe.sslconfig.pekko

import java.util.Collections
import javax.net.ssl._
import com.typesafe.sslconfig.pekko.util.PekkoLoggerFactory
import com.typesafe.sslconfig.ssl._
import com.typesafe.sslconfig.util.LoggerFactory
import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.event.Logging
import scala.annotation.nowarn

@deprecated("Use Tcp and TLS with SSLEngine parameters instead. Setup the SSLEngine with needed parameters.",
  "Akka 2.6.0")
object PekkoSSLConfig extends ExtensionId[PekkoSSLConfig] with ExtensionIdProvider {

  //////////////////// EXTENSION SETUP ///////////////////

  override def get(system: ActorSystem): PekkoSSLConfig = super.get(system)
  override def get(system: ClassicActorSystemProvider): PekkoSSLConfig = super.get(system)
  def apply()(implicit system: ActorSystem): PekkoSSLConfig = super.apply(system)

  override def lookup = PekkoSSLConfig

  override def createExtension(system: ExtendedActorSystem): PekkoSSLConfig =
    new PekkoSSLConfig(system, defaultSSLConfigSettings(system))

  def defaultSSLConfigSettings(system: ActorSystem): SSLConfigSettings = {
    val pekkoOverrides = system.settings.config.getConfig("pekko.ssl-config")
    val defaults = system.settings.config.getConfig("ssl-config")
    SSLConfigFactory.parse(pekkoOverrides.withFallback(defaults))
  }

}

@deprecated("Use Tcp and TLS with SSLEngine parameters instead. Setup the SSLEngine with needed parameters.",
  "Akka 2.6.0")
final class PekkoSSLConfig(system: ExtendedActorSystem, val config: SSLConfigSettings) extends Extension {

  private val mkLogger = new PekkoLoggerFactory(system)

  private val log = Logging(system, classOf[PekkoSSLConfig])
  log.debug("Initializing PekkoSSLConfig extension...")

  /** Can be used to modify the underlying config, most typically used to change a few values in the default config */
  def withSettings(c: SSLConfigSettings): PekkoSSLConfig =
    new PekkoSSLConfig(system, c)

  /**
   * Returns a new [[PekkoSSLConfig]] instance with the settings changed by the given function.
   * Please note that the ActorSystem-wide extension always remains configured via typesafe config,
   * custom ones can be created for special-handling specific connections
   */
  def mapSettings(f: SSLConfigSettings => SSLConfigSettings): PekkoSSLConfig =
    new PekkoSSLConfig(system, f(config))

  /**
   * Returns a new [[PekkoSSLConfig]] instance with the settings changed by the given function.
   * Please note that the ActorSystem-wide extension always remains configured via typesafe config,
   * custom ones can be created for special-handling specific connections
   *
   * Java API
   */
  // Not same signature as mapSettings to allow latter deprecation of this once we hit Scala 2.12
  def convertSettings(f: java.util.function.Function[SSLConfigSettings, SSLConfigSettings]): PekkoSSLConfig =
    new PekkoSSLConfig(system, f.apply(config))

  val hostnameVerifier = buildHostnameVerifier(config)

  /**
   * INTERNAL API
   */
  @InternalApi def useJvmHostnameVerification: Boolean =
    hostnameVerifier match {
      case _: DefaultHostnameVerifier | _: NoopHostnameVerifier => true
      case _                                                    => false
    }

  val sslEngineConfigurator = {
    val sslContext = if (config.default) {
      log.info("ssl-config.default is true, using the JDK's default SSLContext")
      SSLContext.getDefault
    } else {
      // break out the static methods as much as we can...
      val keyManagerFactory = buildKeyManagerFactory(config)
      val trustManagerFactory = buildTrustManagerFactory(config)
      new ConfigSSLContextBuilder(mkLogger, config, keyManagerFactory, trustManagerFactory).build()
    }

    // protocols!
    val defaultParams = sslContext.getDefaultSSLParameters
    val defaultProtocols = defaultParams.getProtocols
    val protocols = configureProtocols(defaultProtocols, config)

    // ciphers!
    val defaultCiphers = defaultParams.getCipherSuites
    val cipherSuites = configureCipherSuites(defaultCiphers, config)

    // apply "loose" settings
    // !! SNI!
    looseDisableSNI(defaultParams)

    new DefaultSSLEngineConfigurator(config, protocols, cipherSuites)
  }

  ////////////////// CONFIGURING //////////////////////

  def buildKeyManagerFactory(ssl: SSLConfigSettings): KeyManagerFactoryWrapper = {
    val keyManagerAlgorithm = ssl.keyManagerConfig.algorithm
    new DefaultKeyManagerFactoryWrapper(keyManagerAlgorithm)
  }

  def buildTrustManagerFactory(ssl: SSLConfigSettings): TrustManagerFactoryWrapper = {
    val trustManagerAlgorithm = ssl.trustManagerConfig.algorithm
    new DefaultTrustManagerFactoryWrapper(trustManagerAlgorithm)
  }

  def buildHostnameVerifier(conf: SSLConfigSettings): HostnameVerifier = {
    conf ne null // @unused unavailable
    val clazz: Class[HostnameVerifier] =
      if (config.loose.disableHostnameVerification)
        classOf[DisabledComplainingHostnameVerifier].asInstanceOf[Class[HostnameVerifier]]
      else config.hostnameVerifierClass.asInstanceOf[Class[HostnameVerifier]]

    val v = system.dynamicAccess
      .createInstanceFor[HostnameVerifier](clazz, Nil)
      .orElse(system.dynamicAccess.createInstanceFor[HostnameVerifier](clazz, List(classOf[LoggerFactory] -> mkLogger)))
      .getOrElse(throw new Exception("Unable to obtain hostname verifier for class: " + clazz))

    log.debug("buildHostnameVerifier: created hostname verifier: {}", v)
    v
  }

  @deprecated("validateDefaultTrustManager is not doing anything since akka 2.6.19 and should not be used",
    "Akka 2.6.19")
  def validateDefaultTrustManager(@nowarn("msg=never used") sslConfig: SSLConfigSettings): Unit = {
    log.warning(
      "validateDefaultTrustManager is not doing anything since akka 2.6.19, it was useful only in Java 7 and below");
  }

  def configureProtocols(existingProtocols: Array[String], sslConfig: SSLConfigSettings): Array[String] = {
    val definedProtocols = sslConfig.enabledProtocols match {
      case Some(configuredProtocols) =>
        // If we are given a specific list of protocols, then return it in exactly that order,
        // assuming that it's actually possible in the SSL context.
        configuredProtocols.filter(existingProtocols.contains).toArray

      case None =>
        // Otherwise, we return the default protocols in the given list.
        Protocols.recommendedProtocols.filter(existingProtocols.contains)
    }

    definedProtocols
  }

  def configureCipherSuites(existingCiphers: Array[String], sslConfig: SSLConfigSettings): Array[String] = {
    val definedCiphers = sslConfig.enabledCipherSuites match {
      case Some(configuredCiphers) =>
        // If we are given a specific list of ciphers, return it in that order.
        configuredCiphers.filter(existingCiphers.contains(_)).toArray

      case None =>
        existingCiphers
    }

    definedCiphers
  }

  // LOOSE SETTINGS //

  private def looseDisableSNI(defaultParams: SSLParameters): Unit = if (config.loose.disableSNI) {
    // this will be logged once for each PekkoSSLConfig
    log.warning(
      "You are using ssl-config.loose.disableSNI=true! " +
      "It is strongly discouraged to disable Server Name Indication, as it is crucial to preventing man-in-the-middle attacks.")

    defaultParams.setServerNames(Collections.emptyList())
    defaultParams.setSNIMatchers(Collections.emptyList())
  }

}
