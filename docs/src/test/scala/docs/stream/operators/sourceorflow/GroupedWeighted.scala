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
