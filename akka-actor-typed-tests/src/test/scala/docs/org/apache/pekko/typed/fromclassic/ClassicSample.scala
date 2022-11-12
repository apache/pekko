/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.org.apache.pekko.typed.fromclassic

// #hello-world-actor
import org.apache.pekko
import pekko.actor.Actor
import pekko.actor.ActorLogging
import pekko.actor.Props

// #hello-world-actor

object ClassicSample {

  // #hello-world-actor
  object HelloWorld {
    final case class Greet(whom: String)
    final case class Greeted(whom: String)

    def props(): Props =
      Props(new HelloWorld)
  }

  class HelloWorld extends Actor with ActorLogging {
    import HelloWorld._

    override def receive: Receive = {
      case Greet(whom) =>
        // #fiddle_code
        log.info("Hello {}!", whom)
        sender() ! Greeted(whom)
    }
  }
  // #hello-world-actor

}
