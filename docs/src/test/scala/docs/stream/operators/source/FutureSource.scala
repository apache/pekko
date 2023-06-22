/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.source

// #sourceFutureSource

import org.apache.pekko
import pekko.NotUsed
import pekko.stream.scaladsl.Source

import scala.concurrent.Future

object FutureSource {
  def sourceCompletionStageSource(): Unit = {
    val userRepository: UserRepository = ??? // an abstraction over the remote service
    val userFutureSource = Source.futureSource(userRepository.loadUsers)
    // ...
  }

  trait UserRepository {
    def loadUsers: Future[Source[User, NotUsed]]
  }

  case class User()
}

// #sourceFutureSource
