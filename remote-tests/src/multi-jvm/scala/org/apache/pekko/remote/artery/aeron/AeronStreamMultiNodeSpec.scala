/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery.aeron

import java.io.File
import java.util.UUID

import io.aeron.CommonContext
import io.aeron.driver.MediaDriver

import org.apache.pekko
import pekko.remote.artery.UdpPortActor
import pekko.remote.testconductor.RoleName
import pekko.remote.testkit.{ MultiNodeConfig, MultiNodeSpec }

abstract class AeronStreamMultiNodeSpec(config: MultiNodeConfig) extends MultiNodeSpec(config) {

  def startDriver(): MediaDriver = {
    val driverContext = new MediaDriver.Context
    // create a random name but include the actor system name for easier debugging
    val uniquePart = UUID.randomUUID().toString
    val randomName = s"${CommonContext.getAeronDirectoryName}${File.separator}${system.name}-$uniquePart"
    driverContext.aeronDirectoryName(randomName)
    val d = MediaDriver.launchEmbedded(driverContext)
    log.info("Started embedded media driver in directory [{}]", d.aeronDirectoryName)
    d
  }

  def channel(roleName: RoleName) = {
    val n = node(roleName)
    val port = MultiNodeSpec.udpPort match {
      case None =>
        system.actorSelection(n / "user" / "updPort") ! UdpPortActor.GetUdpPort
        expectMsgType[Int]
      case Some(p) => p
    }
    s"aeron:udp?endpoint=${n.address.host.get}:$port"
  }
}
