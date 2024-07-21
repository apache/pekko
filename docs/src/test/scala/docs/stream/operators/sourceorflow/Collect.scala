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

package docs.stream.operators.sourceorflow

import org.apache.pekko.NotUsed
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{ Flow, Sink, Source }

object Collect {
  private implicit val system: ActorSystem = null
  // #collect-elements
  trait Message
  final case class Ping(id: Int) extends Message
  final case class Pong(id: Int)
  // #collect-elements

  def collectExample(): Unit = {
    // #collect
    val flow: Flow[Message, Pong, NotUsed] =
      Flow[Message].collect {
        case Ping(id) if id != 0 => Pong(id)
      }
    // #collect
  }

  def collectType(): Unit = {
    // #collectType
    val flow: Flow[Message, Pong, NotUsed] =
      Flow[Message].collectType[Ping].filter(_.id != 0).map(p => Pong(p.id))
    // #collectType
  }

  def collectWhile(): Unit =
    // #collectWhile
    Flow[Message].collectWhile {
      case Ping(id) if id <= 100 => Pong(id)
    }
  // #collectWhile

  def collectFirst(): Unit =
    // #collectFirst
    Source(List(1, 3, 5, 7, 8, 9, 10))
      .collectFirst {
        case elem if elem % 2 == 0 => elem
      }
      .runWith(Sink.foreach(println))
  // expect prints output:
  // 8
  // #collectFirst
}
