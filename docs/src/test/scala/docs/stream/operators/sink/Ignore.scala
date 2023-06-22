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

package docs.stream.operators.sink

import java.util.UUID

import scala.concurrent.Future

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Sink
import org.apache.pekko.stream.scaladsl.Source

object Ignore {
  implicit val system: ActorSystem = ???

  def ignoreExample(): Unit = {
    // #ignore
    val lines: Source[String, NotUsed] = readLinesFromFile()
    val databaseIds: Source[UUID, NotUsed] =
      lines.mapAsync(1)(line => saveLineToDatabase(line))
    databaseIds.mapAsync(1)(uuid => writeIdToFile(uuid)).runWith(Sink.ignore)
    // #ignore
  }

  private def readLinesFromFile(): Source[String, NotUsed] =
    Source.empty

  private def saveLineToDatabase(line: String): Future[UUID] =
    Future.successful(UUID.randomUUID())

  private def writeIdToFile(uuid: UUID): Future[UUID] =
    Future.successful(uuid)

}
