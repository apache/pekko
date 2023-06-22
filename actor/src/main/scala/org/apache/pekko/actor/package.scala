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

package org.apache.pekko

import language.implicitConversions

package object actor {
  @deprecated("implicit conversion is obsolete", "Akka 2.6.13")
  @inline implicit final def actorRef2Scala(ref: ActorRef): ScalaActorRef = ref.asInstanceOf[ScalaActorRef]
  @deprecated("implicit conversion is obsolete", "Akka 2.6.13")
  @inline implicit final def scala2ActorRef(ref: ScalaActorRef): ActorRef = ref.asInstanceOf[ActorRef]
}
