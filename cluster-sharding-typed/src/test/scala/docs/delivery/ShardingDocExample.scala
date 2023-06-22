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

package docs.delivery

//#imports
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import org.apache.pekko
import pekko.Done
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.delivery.ConsumerController
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors
import pekko.cluster.sharding.typed.delivery.ShardingConsumerController
import pekko.util.Timeout

//#imports

object ShardingDocExample {

  // #consumer
  trait DB {
    def save(id: String, value: TodoList.State): Future[Done]
    def load(id: String): Future[TodoList.State]
  }

  object TodoList {

    sealed trait Command

    final case class AddTask(item: String) extends Command
    final case class CompleteTask(item: String) extends Command

    private final case class InitialState(state: State) extends Command
    private final case class SaveSuccess(confirmTo: ActorRef[ConsumerController.Confirmed]) extends Command
    private final case class DBError(cause: Throwable) extends Command

    private final case class CommandDelivery(command: Command, confirmTo: ActorRef[ConsumerController.Confirmed])
        extends Command

    final case class State(tasks: Vector[String])

    def apply(
        id: String,
        db: DB,
        consumerController: ActorRef[ConsumerController.Start[Command]]): Behavior[Command] = {
      Behaviors.setup[Command] { context =>
        new TodoList(context, id, db).start(consumerController)
      }
    }

  }

  class TodoList(context: ActorContext[TodoList.Command], id: String, db: DB) {
    import TodoList._

    private def start(consumerController: ActorRef[ConsumerController.Start[Command]]): Behavior[Command] = {
      context.pipeToSelf(db.load(id)) {
        case Success(value) => InitialState(value)
        case Failure(cause) => DBError(cause)
      }

      Behaviors.receiveMessagePartial {
        case InitialState(state) =>
          val deliveryAdapter: ActorRef[ConsumerController.Delivery[Command]] = context.messageAdapter { delivery =>
            CommandDelivery(delivery.message, delivery.confirmTo)
          }
          consumerController ! ConsumerController.Start(deliveryAdapter)
          active(state)
        case DBError(cause) =>
          throw cause
      }
    }

    private def active(state: State): Behavior[Command] = {
      Behaviors.receiveMessagePartial {
        case CommandDelivery(AddTask(item), confirmTo) =>
          val newState = state.copy(tasks = state.tasks :+ item)
          save(newState, confirmTo)
          active(newState)
        case CommandDelivery(CompleteTask(item), confirmTo) =>
          val newState = state.copy(tasks = state.tasks.filterNot(_ == item))
          save(newState, confirmTo)
          active(newState)
        case SaveSuccess(confirmTo) =>
          confirmTo ! ConsumerController.Confirmed
          Behaviors.same
        case DBError(cause) =>
          throw cause
      }
    }

    private def save(newState: State, confirmTo: ActorRef[ConsumerController.Confirmed]): Unit = {
      context.pipeToSelf(db.save(id, newState)) {
        case Success(_)     => SaveSuccess(confirmTo)
        case Failure(cause) => DBError(cause)
      }
    }
  }
  // #consumer

  // #producer
  import pekko.cluster.sharding.typed.delivery.ShardingProducerController

  object TodoService {
    sealed trait Command

    final case class UpdateTodo(listId: String, item: String, completed: Boolean, replyTo: ActorRef[Response])
        extends Command

    sealed trait Response
    case object Accepted extends Response
    case object Rejected extends Response
    case object MaybeAccepted extends Response

    private final case class WrappedRequestNext(requestNext: ShardingProducerController.RequestNext[TodoList.Command])
        extends Command
    private final case class Confirmed(originalReplyTo: ActorRef[Response]) extends Command
    private final case class TimedOut(originalReplyTo: ActorRef[Response]) extends Command

    def apply(producerController: ActorRef[ShardingProducerController.Command[TodoList.Command]]): Behavior[Command] = {
      Behaviors.setup { context =>
        new TodoService(context).start(producerController)
      }
    }

  }

  class TodoService(context: ActorContext[TodoService.Command]) {
    import TodoService._

    private implicit val askTimeout: Timeout = 5.seconds

    private def start(
        producerController: ActorRef[ShardingProducerController.Start[TodoList.Command]]): Behavior[Command] = {
      val requestNextAdapter: ActorRef[ShardingProducerController.RequestNext[TodoList.Command]] =
        context.messageAdapter(WrappedRequestNext.apply)
      producerController ! ShardingProducerController.Start(requestNextAdapter)

      Behaviors.receiveMessagePartial {
        case WrappedRequestNext(next) =>
          active(next)
        case UpdateTodo(_, _, _, replyTo) =>
          // not hooked up with shardingProducerController yet
          replyTo ! Rejected
          Behaviors.same
      }
    }

    private def active(requestNext: ShardingProducerController.RequestNext[TodoList.Command]): Behavior[Command] = {
      Behaviors.receiveMessage {
        case WrappedRequestNext(next) =>
          active(next)

        case UpdateTodo(listId, item, completed, replyTo) =>
          if (requestNext.bufferedForEntitiesWithoutDemand.getOrElse(listId, 0) >= 100)
            replyTo ! Rejected
          else {
            val requestMsg = if (completed) TodoList.CompleteTask(item) else TodoList.AddTask(item)
            context.ask[ShardingProducerController.MessageWithConfirmation[TodoList.Command], Done](
              requestNext.askNextTo,
              askReplyTo => ShardingProducerController.MessageWithConfirmation(listId, requestMsg, askReplyTo)) {
              case Success(Done) => Confirmed(replyTo)
              case Failure(_)    => TimedOut(replyTo)
            }
          }
          Behaviors.same

        case Confirmed(originalReplyTo) =>
          originalReplyTo ! Accepted
          Behaviors.same

        case TimedOut(originalReplyTo) =>
          originalReplyTo ! MaybeAccepted
          Behaviors.same
      }
    }

  }
  // #producer

  def illustrateInit(): Unit = {
    Behaviors.setup[Nothing] { context =>
      // #init
      import org.apache.pekko
      import pekko.cluster.sharding.typed.scaladsl.ClusterSharding
      import pekko.cluster.sharding.typed.scaladsl.Entity
      import pekko.cluster.sharding.typed.scaladsl.EntityTypeKey
      import pekko.cluster.typed.Cluster

      val db: DB = ???

      val system = context.system

      val TypeKey = EntityTypeKey[ConsumerController.SequencedMessage[TodoList.Command]]("todo")

      val region = ClusterSharding(system).init(Entity(TypeKey)(entityContext =>
        ShardingConsumerController(start => TodoList(entityContext.entityId, db, start))))

      val selfAddress = Cluster(system).selfMember.address
      val producerId = s"todo-producer-${selfAddress.host}:${selfAddress.port}"

      val producerController =
        context.spawn(ShardingProducerController(producerId, region, durableQueueBehavior = None), "producerController")

      context.spawn(TodoService(producerController), "producer")
      // #init

      Behaviors.empty
    }
  }

}
