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

package docs.stream.operators.sourceorflow
import org.apache.pekko.stream.scaladsl.Source
import scala.collection.immutable

object GroupedWeighted {
  def groupedWeightedExample(): Unit = {
    import org.apache.pekko.actor.ActorSystem

    implicit val system: ActorSystem = ActorSystem()

    // #groupedWeighted
    val collections = immutable.Iterable(Seq(1, 2), Seq(3, 4), Seq(5, 6))
    Source[Seq[Int]](collections).groupedWeighted(4)(_.length).runForeach(println)
    // Vector(Seq(1, 2), Seq(3, 4))
    // Vector(Seq(5, 6))

    Source[Seq[Int]](collections).groupedWeighted(3)(_.length).runForeach(println)
    // Vector(Seq(1, 2), Seq(3, 4))
    // Vector(Seq(5, 6))
    // #groupedWeighted
  }

}
