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

package docs.org.apache.pekko.typed

import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.testkit.typed.scaladsl.{ LogCapturing, ScalaTestWithActorTestKit }

object StashDocSpec {
  // #stashing
  import scala.concurrent.Future
  import scala.util.{ Failure, Success }

  import org.apache.pekko
  import pekko.Done
  import pekko.actor.typed.scaladsl.{ ActorContext, Behaviors, StashBuffer }
  import pekko.actor.typed.{ ActorRef, Behavior }

  trait DB {
    def save(id: String, value: String): Future[Done]
    def load(id: String): Future[String]
  }

  object DataAccess {
    sealed trait Command
    final case class Save(value: String, replyTo: ActorRef[Done]) extends Command
    final case class Get(replyTo: ActorRef[String]) extends Command
    private final case class InitialState(value: String) extends Command
    private case object SaveSuccess extends Command
    private final case class DBError(cause: Throwable) extends Command

    def apply(id: String, db: DB): Behavior[Command] = {
      Behaviors.withStash(100) { buffer =>
        Behaviors.setup[Command] { context =>
          new DataAccess(context, buffer, id, db).start()
        }
      }
    }
  }

  class DataAccess(
      context: ActorContext[DataAccess.Command],
      buffer: StashBuffer[DataAccess.Command],
      id: String,
      db: DB) {
    import DataAccess._

    private def start(): Behavior[Command] = {
      context.pipeToSelf(db.load(id)) {
        case Success(value) => InitialState(value)
        case Failure(cause) => DBError(cause)
      }

      Behaviors.receiveMessage {
        case InitialState(value) =>
          // now we are ready to handle stashed messages if any
          buffer.unstashAll(active(value))
        case DBError(cause) =>
          throw cause
        case other =>
          // stash all other messages for later processing
          buffer.stash(other)
          Behaviors.same
      }
    }

    private def active(state: String): Behavior[Command] = {
      Behaviors.receiveMessagePartial {
        case Get(replyTo) =>
          replyTo ! state
          Behaviors.same
        case Save(value, replyTo) =>
          context.pipeToSelf(db.save(id, value)) {
            case Success(_)     => SaveSuccess
            case Failure(cause) => DBError(cause)
          }
          saving(value, replyTo)
      }
    }

    private def saving(state: String, replyTo: ActorRef[Done]): Behavior[Command] = {
      Behaviors.receiveMessage {
        case SaveSuccess =>
          replyTo ! Done
          buffer.unstashAll(active(state))
        case DBError(cause) =>
          throw cause
        case other =>
          buffer.stash(other)
          Behaviors.same
      }
    }

  }
  // #stashing
}

class StashDocSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with LogCapturing {
  import StashDocSpec.{ DB, DataAccess }
  import pekko.Done

  import scala.concurrent.Future

  "Stashing docs" must {

    "illustrate stash and unstashAll" in {

      val db = new DB {
        override def save(id: String, value: String): Future[Done] = Future.successful(Done)
        override def load(id: String): Future[String] = Future.successful("TheValue")
      }
      val dataAccess = spawn(DataAccess(id = "17", db))
      val getProbe = createTestProbe[String]()
      dataAccess ! DataAccess.Get(getProbe.ref)
      getProbe.expectMessage("TheValue")

      val saveProbe = createTestProbe[Done]()
      dataAccess ! DataAccess.Save("UpdatedValue", saveProbe.ref)
      dataAccess ! DataAccess.Get(getProbe.ref)
      saveProbe.expectMessage(Done)
      getProbe.expectMessage("UpdatedValue")

      dataAccess ! DataAccess.Get(getProbe.ref)
      getProbe.expectMessage("UpdatedValue")
    }
  }
}
