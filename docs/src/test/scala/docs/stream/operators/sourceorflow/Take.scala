/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

object Take {
  def takeExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem
    import org.apache.pekko.stream.scaladsl.Source

    implicit val system: ActorSystem = ActorSystem()

    // #take
    Source(1 to 5).take(3).runForeach(println)
    // 1
    // 2
    // 3
    // #take
  }
}
