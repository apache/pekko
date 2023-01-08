/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed.fromclassic

// #hello-world-actor
import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.Behavior
import pekko.actor.typed.scaladsl.AbstractBehavior
import pekko.actor.typed.scaladsl.ActorContext
import pekko.actor.typed.scaladsl.Behaviors

// #hello-world-actor

object TypedSample {

  // #hello-world-actor
  object HelloWorld {
    final case class Greet(whom: String, replyTo: ActorRef[Greeted])
    final case class Greeted(whom: String, from: ActorRef[Greet])

    def apply(): Behavior[HelloWorld.Greet] =
      Behaviors.setup(context => new HelloWorld(context))
  }

  class HelloWorld(context: ActorContext[HelloWorld.Greet]) extends AbstractBehavior[HelloWorld.Greet](context) {
    import HelloWorld._

    override def onMessage(message: Greet): Behavior[Greet] = {
      context.log.info("Hello {}!", message.whom)
      message.replyTo ! Greeted(message.whom, context.self)
      this
    }
  }
  // #hello-world-actor

  // #children
  object Parent {
    sealed trait Command
    case class DelegateToChild(name: String, message: Child.Command) extends Command
    private case class ChildTerminated(name: String) extends Command

    def apply(): Behavior[Command] = {
      def updated(children: Map[String, ActorRef[Child.Command]]): Behavior[Command] = {
        Behaviors.receive { (context, command) =>
          command match {
            case DelegateToChild(name, childCommand) =>
              children.get(name) match {
                case Some(ref) =>
                  ref ! childCommand
                  Behaviors.same
                case None =>
                  val ref = context.spawn(Child(), name)
                  context.watchWith(ref, ChildTerminated(name))
                  ref ! childCommand
                  updated(children + (name -> ref))
              }

            case ChildTerminated(name) =>
              updated(children - name)
          }
        }
      }

      updated(Map.empty)
    }
  }
  // #children

  object Child {
    sealed trait Command

    def apply(): Behavior[Command] = Behaviors.empty
  }

}
