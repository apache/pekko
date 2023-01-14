/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote

import com.typesafe.config.ConfigFactory

import org.apache.pekko.actor.ActorMailboxSpec

class RemoteActorMailboxSpec
    extends ActorMailboxSpec(
      ConfigFactory.parseString("""pekko.actor.provider = remote""").withFallback(ActorMailboxSpec.mailboxConf)) {}
