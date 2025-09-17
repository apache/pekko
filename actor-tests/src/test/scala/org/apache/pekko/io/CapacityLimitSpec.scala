/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io

import org.apache.pekko
import pekko.io.Tcp._
import pekko.testkit.SocketUtil.temporaryServerAddresses
import pekko.testkit.{ PekkoSpec, TestProbe }

class CapacityLimitSpec extends PekkoSpec("""
    pekko.loglevel = ERROR
    pekko.io.tcp.max-channels = 4
    """) with TcpIntegrationSpecSupport {

  "The TCP transport implementation" should {

    "reply with CommandFailed to a Bind or Connect command if max-channels capacity has been reached" in new TestSetup(
      runClientInExtraSystem = false) {
      establishNewClientConnection()

      // we now have three channels registered: a listener, a server connection and a client connection
      // so register one more channel
      val commander = TestProbe()
      val addresses = temporaryServerAddresses(2)
      commander.send(IO(Tcp), Bind(bindHandler.ref, addresses(0)))
      commander.expectMsg(Bound(addresses(0)))

      // we are now at the configured max-channel capacity of 4

      val bindToFail = Bind(bindHandler.ref, addresses(1))
      commander.send(IO(Tcp), bindToFail)
      (commander.expectMsgType[CommandFailed].cmd should be).theSameInstanceAs(bindToFail)

      val connectToFail = Connect(endpoint)
      commander.send(IO(Tcp), connectToFail)
      (commander.expectMsgType[CommandFailed].cmd should be).theSameInstanceAs(connectToFail)
    }

  }

}
