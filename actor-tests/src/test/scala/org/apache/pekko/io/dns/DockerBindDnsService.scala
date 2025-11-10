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

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.control.NonFatal
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.async.ResultCallback
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model._
import com.github.dockerjava.core.{ DefaultDockerClientConfig, DockerClientConfig, DockerClientImpl }
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient

import org.apache.pekko
import pekko.io.{ Dns, IO }
import pekko.util.Timeout

import pekko.testkit.PekkoSpec
import org.scalatest.concurrent.Eventually
import com.typesafe.config.Config

abstract class DockerBindDnsService(config: Config) extends PekkoSpec(config) with Eventually {

  val dockerConfig: DockerClientConfig = DefaultDockerClientConfig
    .createDefaultConfigBuilder()
    .build();

  val httpClient: ApacheDockerHttpClient = new ApacheDockerHttpClient.Builder()
    .dockerHost(dockerConfig.getDockerHost())
    .sslConfig(dockerConfig.getSSLConfig())
    .build();

  val client: DockerClient = DockerClientImpl.getInstance(dockerConfig, httpClient);

  val hostPort: Int

  var id: Option[String] = None

  def dockerAvailable() = Try(client.pingCmd().exec()).isSuccess

  override def atStartup(): Unit = {
    log.info("Running on port port {}", hostPort)
    super.atStartup()

    // Use cytopia/bind which supports multi-platform including ARM64 for Apple M series machines
    // and is battle-tested with 5M+ downloads (vs 322 for jonasal/bind)
    // https://github.com/cytopia/docker-bind
    val image = "cytopia/bind:latest"
    try {
      client
        .pullImageCmd(image)
        .start()
        .awaitCompletion()
    } catch {
      case NonFatal(_) =>
        log.warning(s"Failed to pull docker image [$image], is docker running?")
        return
    }

    val containerName = "pekko-test-dns-" + getClass.getCanonicalName

    val containerCommand: CreateContainerCmd = client
      .createContainerCmd(image)
      .withName(containerName)
      .withEnv(
        "DNS_A=a-single.bar.example=192.168.2.20",
        "DNS_CNAME=cname-ext.foo.test=a-single.bar.example",
        "ALLOW_RECURSION=any",
        "DNS_FORWARDER=8.8.8.8"
      )
      .withHostConfig(
        HostConfig.newHostConfig()
          .withBinds(
            Bind.parse(s"${System.getProperty("user.dir")}/actor-tests/src/test/bind/etc:/etc/bind/local-config"),
            Bind.parse(
              s"${System.getProperty("user.dir")}/actor-tests/src/test/bind/etc/named.conf.local:/etc/bind/named.conf.local"),
            Bind.parse(
              s"${System.getProperty("user.dir")}/actor-tests/src/test/bind/etc/named.conf.options:/etc/bind/named.conf.options"))
          .withPortBindings(
            PortBinding.parse(s"$hostPort:53/tcp"),
            PortBinding.parse(s"$hostPort:53/udp")))

    client
      .listContainersCmd()
      .exec()
      .asScala
      .find((c: Container) => c.getNames().exists(_.contains(containerName)))
      .foreach((c: Container) => {
        if ("running" == c.getState()) {
          client.killContainerCmd(c.getId()).exec()
        }
        client.removeContainerCmd(c.getId()).exec()
      })

    val creation = containerCommand.exec()
    if (creation.getWarnings() != null)
      creation.getWarnings() should have(size(0))
    id = Some(creation.getId())

    client.startContainerCmd(creation.getId()).exec()
    val reader = new StringBuilderLogReader

    eventually(timeout(25.seconds)) {
      client
        .logContainerCmd(creation.getId())
        .withStdOut(true)
        .withStdErr(true)
        .exec(reader)

      reader.toString should include("Starting BIND")
    }
    eventually(timeout(25.seconds)) {
      import pekko.pattern.ask
      implicit val timeout: Timeout = 2.seconds
      (IO(Dns) ? DnsProtocol.Resolve("a-single.foo.test", DnsProtocol.Ip(ipv6 = false))).mapTo[
        DnsProtocol.Resolved].futureValue
    }
  }

  def dumpNameserverLogs(): Unit = {
    id.foreach(id => {
      val reader = new StringBuilderLogReader
      client
        .logContainerCmd(id)
        .withStdOut(true)
        .exec(reader)
        .awaitCompletion()

      log.info("Nameserver std out: {} ", reader.toString())
    })
    id.foreach(id => {
      val reader = new StringBuilderLogReader
      client
        .logContainerCmd(id)
        .withStdErr(true)
        .exec(reader)
        .awaitCompletion()
      log.info("Nameserver std err: {} ", reader.toString())
    })
  }

  override def afterTermination(): Unit = {
    super.afterTermination()
    id.foreach(id => client.killContainerCmd(id).exec())
    id.foreach(id => client.removeContainerCmd(id).exec())

    client.close()
  }
}

class StringBuilderLogReader extends ResultCallback.Adapter[Frame] {

  lazy val builder: StringBuilder = new StringBuilder

  override def onNext(item: Frame): Unit = {
    builder.append(new String(item.getPayload))
    super.onNext(item)
  }

  override def toString(): String = builder.toString()
}
