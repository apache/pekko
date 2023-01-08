/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators

import org.apache.pekko.testkit.PekkoSpec

class WithContextSpec extends PekkoSpec {

  "use asSourceWithContext" in {
    // #asSourceWithContext
    import org.apache.pekko
    import pekko.NotUsed
    import pekko.stream.scaladsl.Source
    import pekko.stream.scaladsl.SourceWithContext
    import scala.collection.immutable

    // values with their contexts as tuples
    val values: immutable.Seq[(String, Int)] = immutable.Seq("eins" -> 1, "zwei" -> 2, "drei" -> 3)

    // a regular source with the tuples as elements
    val source: Source[(String, Int), NotUsed] = Source(values)

    // split the tuple into stream elements and their context
    val sourceWithContext: SourceWithContext[String, Int, NotUsed] =
      source
        .asSourceWithContext(_._2) // pick the second tuple element as context
        .map(_._1) // keep the first tuple element as stream element

    val mapped: SourceWithContext[String, Int, NotUsed] = sourceWithContext
      // regular operators apply to the element without seeing the context
      .map(s => s.reverse)

    // running the source and asserting the outcome
    import org.apache.pekko.stream.scaladsl.Sink
    val result = mapped.runWith(Sink.seq)
    result.futureValue should contain theSameElementsInOrderAs immutable.Seq("snie" -> 1, "iewz" -> 2, "ierd" -> 3)
    // #asSourceWithContext
  }

  "use asFlowWithContext" in {
    // #asFlowWithContext
    import org.apache.pekko
    import pekko.NotUsed
    import pekko.stream.scaladsl.Flow
    import pekko.stream.scaladsl.FlowWithContext
    // a regular flow with pairs as elements
    val flow: Flow[(String, Int), (String, Int), NotUsed] = // ???
      // #asFlowWithContext
      Flow[(String, Int)]
    // #asFlowWithContext

    // Declare the "flow with context"
    // ingoing: String and Integer
    // outgoing: String and Integer
    val flowWithContext: FlowWithContext[String, Int, String, Int, NotUsed] =
      // convert the flow of pairs into a "flow with context"
      flow
        .asFlowWithContext[String, Int, Int](
          // at the end of this flow: put the elements and the context back into a tuple
          collapseContext = Tuple2.apply)(
          // pick the second element of the incoming pair as context
          extractContext = _._2)
        .map(_._1) // keep the first pair element as stream element

    val mapped = flowWithContext
      // regular operators apply to the element without seeing the context
      .map(_.reverse)

    // running the flow with some sample data and asserting the outcome
    import pekko.stream.scaladsl.Source
    import pekko.stream.scaladsl.Sink
    import scala.collection.immutable

    val values: immutable.Seq[(String, Int)] = immutable.Seq("eins" -> 1, "zwei" -> 2, "drei" -> 3)
    val source = Source(values).asSourceWithContext(_._2).map(_._1)

    val result = source.via(mapped).runWith(Sink.seq)
    result.futureValue should contain theSameElementsInOrderAs immutable.Seq("snie" -> 1, "iewz" -> 2, "ierd" -> 3)
    // #asFlowWithContext
  }
}
