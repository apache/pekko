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
import scala.util.Try
import scala.util.control.NonFatal

import com.typesafe.config.Config
import com.github.dockerjava.core.DockerClientConfig
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.async.ResultCallback;
import org.scalatest.concurrent.Eventually

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.ccompat.JavaConverters._
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import com.github.dockerjava.api.command.CreateContainerCmd
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.api.model.Volume
import com.github.dockerjava.core.DockerClientImpl
import com.github.dockerjava.api.model.Bind

abstract class DockerBindDnsService(config: Config) extends PekkoSpec(config) with Eventually {

  val dockerConfig: DockerClientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

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

    // https://github.com/sameersbn/docker-bind/pull/61
    val image = "raboof/bind:9.11.3-20180713-nochown"

    try {
      client
        .pullImageCmd(image)
        .start()
    } catch {
      case NonFatal(_) =>
        log.warning(s"Failed to pull docker image [$image], is docker running?")
        return
    }

    val containerName = "pekko-test-dns-" + getClass.getCanonicalName

    val containerCommand: CreateContainerCmd = client
      .createContainerCmd(image)
      .withName(containerName)
      .withEnv("NO_CHOWN=true")
      .withCmd("-4")
      .withHostConfig(
        HostConfig.newHostConfig()
          .withPortBindings(
            PortBinding.parse(s"$hostPort:53/tcp"),
            PortBinding.parse(s"$hostPort:53/udp"))
          .withBinds(new Bind(new java.io.File("actor-tests/src/test/bind/").getAbsolutePath,
            new Volume("/data/bind"))))

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
        .withStdErr(true)
        .exec(reader)

      reader.toString should include("all zones loaded")
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
