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

package docs.stream.operators.source

import org.apache.pekko.Done
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.Source

import scala.concurrent.Future

object UnfoldResourceAsync {

  // imaginary async API we need to use
  // #unfoldResource-async-api
  trait Database {
    // blocking query
    def doQuery(): Future[QueryResult]
  }
  trait QueryResult {
    def hasMore(): Future[Boolean]
    def nextEntry(): Future[DatabaseEntry]
    def close(): Future[Unit]
  }
  trait DatabaseEntry
  // #unfoldResource-async-api

  def unfoldResourceExample(): Unit = {
    implicit val actorSystem = ActorSystem()
    implicit val ex = actorSystem.dispatcher
    // #unfoldResourceAsync
    // we don't actually have one, it was just made up for the sample
    val database: Database = ???

    val queryResultSource: Source[DatabaseEntry, NotUsed] =
      Source.unfoldResourceAsync[DatabaseEntry, QueryResult](
        // open
        () => database.doQuery(),
        // read
        query =>
          query.hasMore().flatMap {
            case false => Future.successful(None)
            case true  => query.nextEntry().map(dbEntry => Some(dbEntry))
          },
        // close
        query => query.close().map(_ => Done))

    // process each element
    queryResultSource.runForeach(println)
    // #unfoldResourceAsync
  }

}
