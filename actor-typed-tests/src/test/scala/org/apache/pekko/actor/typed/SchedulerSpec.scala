/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed

import scala.concurrent.duration._

class SchedulerSpec {

  def compileOnly(): Unit = {
    val system: ActorSystem[Nothing] = ???
    import system.executionContext

    // verify a lambda works
    system.scheduler.scheduleWithFixedDelay(10.milliseconds, 10.milliseconds)(() => system.log.info("Woho!"))
    system.scheduler.scheduleAtFixedRate(10.milliseconds, 10.milliseconds)(() => system.log.info("Woho!"))
    system.scheduler.scheduleOnce(10.milliseconds, () => system.log.info("Woho!"))
  }

}
