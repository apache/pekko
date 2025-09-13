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

package docs.stream.operators.source

import scala.concurrent.Future
import scala.concurrent.duration._

import org.apache.pekko
import pekko.NotUsed
import pekko.actor.typed.{ ActorRef, ActorSystem }
import pekko.actor.typed.scaladsl.AskPattern._
import pekko.stream.scaladsl.Source
import pekko.util.{ ByteString, Timeout }

object UnfoldAsync {

  // #unfoldAsync-actor-protocol
  object DataActor {
    sealed trait Command
    case class FetchChunk(offset: Long, replyTo: ActorRef[Chunk]) extends Command
    case class Chunk(bytes: ByteString)
    // #unfoldAsync-actor-protocol

  }
  implicit val system: ActorSystem[Nothing] = ???

  def unfoldAsyncExample(): Unit = {
    // #unfoldAsync
    // actor we can query for data with an offset
    val dataActor: ActorRef[DataActor.Command] = ???
    import system.executionContext

    implicit val askTimeout: Timeout = 3.seconds
    val startOffset = 0L
    val byteSource: Source[ByteString, NotUsed] =
      Source.unfoldAsync(startOffset) { currentOffset =>
        // ask for next chunk
        val nextChunkFuture: Future[DataActor.Chunk] =
          dataActor.ask(DataActor.FetchChunk(currentOffset, _))

        nextChunkFuture.map { chunk =>
          val bytes = chunk.bytes
          if (bytes.isEmpty) None // end of data
          else Some((currentOffset + bytes.length, bytes))
        }
      }
    // #unfoldAsync
  }

}
