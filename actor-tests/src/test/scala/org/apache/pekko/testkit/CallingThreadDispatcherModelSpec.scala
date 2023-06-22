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

package org.apache.pekko.testkit

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.Config

import org.apache.pekko
import pekko.actor.dispatch.ActorModelSpec
import pekko.dispatch.DispatcherPrerequisites
import pekko.dispatch.MessageDispatcher
import pekko.dispatch.MessageDispatcherConfigurator

object CallingThreadDispatcherModelSpec {
  import ActorModelSpec._

  val config = {
    """
      boss {
        executor = thread-pool-executor
        type = PinnedDispatcher
      }
    """ +
    // use unique dispatcher id for each test, since MessageDispatcherInterceptor holds state
    (for (n <- 1 to 30)
      yield """
        test-calling-thread-%s {
          type = "org.apache.pekko.testkit.CallingThreadDispatcherModelSpec$CallingThreadDispatcherInterceptorConfigurator"
        }""".format(n)).mkString
  }

  class CallingThreadDispatcherInterceptorConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
      extends MessageDispatcherConfigurator(config, prerequisites) {

    private val instance: MessageDispatcher =
      new CallingThreadDispatcher(this) with MessageDispatcherInterceptor {
        override def id: String = config.getString("id")
      }

    override def dispatcher(): MessageDispatcher = instance

  }

}

class CallingThreadDispatcherModelSpec extends ActorModelSpec(CallingThreadDispatcherModelSpec.config) {
  import ActorModelSpec._

  val dispatcherCount = new AtomicInteger()

  override def interceptedDispatcher(): MessageDispatcherInterceptor = {
    // use new id for each test, since the MessageDispatcherInterceptor holds state
    system.dispatchers
      .lookup("test-calling-thread-" + dispatcherCount.incrementAndGet())
      .asInstanceOf[MessageDispatcherInterceptor]
  }
  override def dispatcherType = "Calling Thread Dispatcher"

}
