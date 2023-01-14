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

package docs.event

import org.apache.pekko
import pekko.actor.{ Actor, DeadLetter, Props }
import pekko.testkit.PekkoSpec

object LoggingDocSpec {

  // #my-actor
  import org.apache.pekko.event.Logging

  class MyActor extends Actor {
    val log = Logging(context.system, this)
    override def preStart() = {
      log.debug("Starting")
    }
    override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
      log.error(reason, "Restarting due to [{}] when processing [{}]", reason.getMessage, message.getOrElse(""))
    }
    def receive = {
      case "test" => log.info("Received test")
      case x      => log.warning("Received unknown message: {}", x)
    }
  }
  // #my-actor

  import org.apache.pekko.event.Logging

  class MdcActor extends Actor {
    val log = Logging(this)
    def receive = {

      case _ => {
        // #mdc
        val mdc = Map("requestId" -> 1234, "visitorId" -> 5678)
        log.mdc(mdc)

        // Log something
        log.info("Starting new request")

        log.clearMDC()
        // #mdc
      }
    }
  }

  // #mdc-actor
  import Logging.MDC

  final case class Req(work: String, visitorId: Int)

  class MdcActorMixin extends Actor with pekko.actor.DiagnosticActorLogging {
    var reqId = 0

    override def mdc(currentMessage: Any): MDC = {
      reqId += 1
      val always = Map("requestId" -> reqId)
      val perMessage = currentMessage match {
        case r: Req => Map("visitorId" -> r.visitorId)
        case _      => Map()
      }
      always ++ perMessage
    }

    def receive: Receive = {
      case r: Req => {
        log.info(s"Starting new request: ${r.work}")
      }
    }
  }

  // #mdc-actor

  // #my-event-listener
  import org.apache.pekko
  import pekko.event.Logging.Debug
  import pekko.event.Logging.Error
  import pekko.event.Logging.Info
  import pekko.event.Logging.InitializeLogger
  import pekko.event.Logging.LoggerInitialized
  import pekko.event.Logging.Warning

  class MyEventListener extends Actor {
    def receive = {
      case InitializeLogger(_)                        => sender() ! LoggerInitialized
      case Error(cause, logSource, logClass, message) => // ...
      case Warning(logSource, logClass, message)      => // ...
      case Info(logSource, logClass, message)         => // ...
      case Debug(logSource, logClass, message)        => // ...
    }
  }
  // #my-event-listener

  // #my-source
  import org.apache.pekko
  import pekko.actor.ActorSystem
  import pekko.event.LogSource

  object MyType {
    implicit val logSource: LogSource[AnyRef] = new LogSource[AnyRef] {
      def genString(o: AnyRef): String = o.getClass.getName
      override def getClazz(o: AnyRef): Class[_] = o.getClass
    }
  }

  class MyType(system: ActorSystem) {
    import MyType._
    import pekko.event.Logging

    val log = Logging(system, this)
  }
  // #my-source

  object Listeners {
    def println(s: Any) = ()

    // #deadletters
    import org.apache.pekko.actor.{ Actor, DeadLetter, Props }

    class DeadLetterListener extends Actor {
      def receive = {
        case d: DeadLetter => println(d)
      }
    }
    // #deadletters

    // #superclass-subscription-eventstream
    abstract class AllKindsOfMusic { def artist: String }
    case class Jazz(artist: String) extends AllKindsOfMusic
    case class Electronic(artist: String) extends AllKindsOfMusic

    class Listener extends Actor {
      def receive = {
        case m: Jazz       => println(s"${self.path.name} is listening to: ${m.artist}")
        case m: Electronic => println(s"${self.path.name} is listening to: ${m.artist}")
      }
    }
    // #superclass-subscription-eventstream
  }

}

class LoggingDocSpec extends PekkoSpec {

  import LoggingDocSpec.{ MdcActor, MdcActorMixin, MyActor, Req }

  "use a logging actor" in {
    val myActor = system.actorOf(Props[MyActor]())
    myActor ! "test"
  }

  "use a MDC logging actor" in {
    val mdcActor = system.actorOf(Props[MdcActor]())
    mdcActor ! "some request"
  }

  "use a MDC logging actor by mixin" in {
    val mdcActor = system.actorOf(Props[MdcActorMixin]())
    mdcActor ! Req("some request", 5678)
  }

  "allow registration to dead letters" in {
    import LoggingDocSpec.Listeners._
    // #deadletters

    val listener = system.actorOf(Props[DeadLetterListener]())
    system.eventStream.subscribe(listener, classOf[DeadLetter])
    // #deadletters
  }

  "demonstrate superclass subscriptions on eventStream" in {
    import LoggingDocSpec.Listeners._
    // #superclass-subscription-eventstream

    val jazzListener = system.actorOf(Props[Listener]())
    val musicListener = system.actorOf(Props[Listener]())
    system.eventStream.subscribe(jazzListener, classOf[Jazz])
    system.eventStream.subscribe(musicListener, classOf[AllKindsOfMusic])

    // only musicListener gets this message, since it listens to *all* kinds of music:
    system.eventStream.publish(Electronic("Parov Stelar"))

    // jazzListener and musicListener will be notified about Jazz:
    system.eventStream.publish(Jazz("Sonny Rollins"))
    // #superclass-subscription-eventstream
  }

  "allow registration to suppressed dead letters" in {
    import org.apache.pekko.actor.Props
    val listener = system.actorOf(Props[MyActor]())

    // #suppressed-deadletters
    import org.apache.pekko.actor.SuppressedDeadLetter
    system.eventStream.subscribe(listener, classOf[SuppressedDeadLetter])
    // #suppressed-deadletters

    // #all-deadletters
    import org.apache.pekko.actor.AllDeadLetters
    system.eventStream.subscribe(listener, classOf[AllDeadLetters])
    // #all-deadletters
  }

  "demonstrate logging more arguments" in {
    // #array
    val args = Array("The", "brown", "fox", "jumps", 42)
    system.log.debug("five parameters: {}, {}, {}, {}, {}", args)
    // #array
  }

}
