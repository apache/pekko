/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.ActorRef
import pekko.stream.WatchedActorTerminatedException
import pekko.stream.scaladsl.Flow

object Watch {

  def someActor(): ActorRef = ???

  def watchExample(): Unit = {
    // #watch
    val ref: ActorRef = someActor()
    val flow: Flow[String, String, NotUsed] =
      Flow[String].watch(ref).recover {
        case _: WatchedActorTerminatedException => s"$ref terminated"
      }
    // #watch
  }

}
