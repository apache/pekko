/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.impl.fusing

import org.apache.pekko

import pekko.stream.scaladsl.{ Balance, Broadcast, Merge, Zip }
import pekko.stream.testkit.StreamSpec

class GraphInterpreterSpec extends StreamSpec with GraphInterpreterSpecKit {
  import GraphStages._

  "GraphInterpreter" must {

    // Reusable components
    val identity = GraphStages.identity[Int]
    val detach = detacher[Int]
    val zip = Zip[Int, String]()
    val bcast = Broadcast[Int](2)
    val merge = Merge[Int](2)
    val balance = Balance[Int](2)

    "implement identity" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      builder(identity).connect(source, identity.in).connect(identity.out, sink).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1)))
    }

    "implement chained identity" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      // Constructing an assembly by hand and resolving ambiguities
      val (logics, _, _) = GraphInterpreterSpecKit.createLogics(Array(identity), Array(source), Array(sink))
      val connections = GraphInterpreterSpecKit.createLinearFlowConnections(logics.toIndexedSeq)

      manualInit(logics, connections)

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1)))
    }
    "implement detacher stage" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      builder(detach).connect(source, detach.shape.in).connect(detach.shape.out, sink).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1), RequestOne(source)))

      // Source waits
      source.onNext(2)
      lastEvents() should ===(Set.empty[TestEvent])

      // "pushAndPull"
      sink.requestOne()
      lastEvents() should ===(Set(OnNext(sink, 2), RequestOne(source)))

      // Sink waits
      sink.requestOne()
      lastEvents() should ===(Set.empty[TestEvent])

      // "pushAndPull"
      source.onNext(3)
      lastEvents() should ===(Set(OnNext(sink, 3), RequestOne(source)))
    }

    "implement Zip" in new TestSetup {
      val source1 = new UpstreamProbe[Int]("source1")
      val source2 = new UpstreamProbe[String]("source2")
      val sink = new DownstreamProbe[(Int, String)]("sink")

      builder(zip).connect(source1, zip.in0).connect(source2, zip.in1).connect(zip.out, sink).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source1), RequestOne(source2)))

      source1.onNext(42)
      lastEvents() should ===(Set.empty[TestEvent])

      source2.onNext("Meaning of life")
      lastEvents() should ===(Set(OnNext(sink, (42, "Meaning of life")), RequestOne(source1), RequestOne(source2)))
    }

    "implement Broadcast" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink1 = new DownstreamProbe[Int]("sink1")
      val sink2 = new DownstreamProbe[Int]("sink2")

      builder(bcast).connect(source, bcast.in).connect(bcast.out(0), sink1).connect(bcast.out(1), sink2).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink1.requestOne()
      lastEvents() should ===(Set.empty[TestEvent])

      sink2.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink1, 1), OnNext(sink2, 1)))

    }

    "implement broadcast-zip" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[(Int, Int)]("sink")
      val zip = new Zip[Int, Int]

      builder(zip, bcast)
        .connect(source, bcast.in)
        .connect(bcast.out(0), zip.in0)
        .connect(bcast.out(1), zip.in1)
        .connect(zip.out, sink)
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, (1, 1)), RequestOne(source)))

      sink.requestOne()
      source.onNext(2)
      lastEvents() should ===(Set(OnNext(sink, (2, 2)), RequestOne(source)))

    }

    "implement zip-broadcast" in new TestSetup {
      val source1 = new UpstreamProbe[Int]("source1")
      val source2 = new UpstreamProbe[Int]("source2")
      val sink1 = new DownstreamProbe[(Int, Int)]("sink")
      val sink2 = new DownstreamProbe[(Int, Int)]("sink2")
      val zip = new Zip[Int, Int]
      val bcast = Broadcast[(Int, Int)](2)

      builder(bcast, zip)
        .connect(source1, zip.in0)
        .connect(source2, zip.in1)
        .connect(zip.out, bcast.in)
        .connect(bcast.out(0), sink1)
        .connect(bcast.out(1), sink2)
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink1.requestOne()
      lastEvents() should ===(Set(RequestOne(source1), RequestOne(source2)))

      sink2.requestOne()

      source1.onNext(1)
      lastEvents() should ===(Set.empty[TestEvent])

      source2.onNext(2)
      lastEvents() should ===(
        Set(OnNext(sink1, (1, 2)), OnNext(sink2, (1, 2)), RequestOne(source1), RequestOne(source2)))

    }

    "implement merge" in new TestSetup {
      val source1 = new UpstreamProbe[Int]("source1")
      val source2 = new UpstreamProbe[Int]("source2")
      val sink = new DownstreamProbe[Int]("sink")

      builder(merge).connect(source1, merge.in(0)).connect(source2, merge.in(1)).connect(merge.out, sink).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source1), RequestOne(source2)))

      source1.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1), RequestOne(source1)))

      source2.onNext(2)
      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(OnNext(sink, 2), RequestOne(source2)))

      sink.requestOne()
      lastEvents() should ===(Set.empty[TestEvent])

      source2.onNext(3)
      lastEvents() should ===(Set(OnNext(sink, 3), RequestOne(source2)))

      sink.requestOne()
      lastEvents() should ===(Set.empty[TestEvent])

      source1.onNext(4)
      lastEvents() should ===(Set(OnNext(sink, 4), RequestOne(source1)))

    }

    "implement balance" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink1 = new DownstreamProbe[Int]("sink1")
      val sink2 = new DownstreamProbe[Int]("sink2")

      builder(balance).connect(source, balance.in).connect(balance.out(0), sink1).connect(balance.out(1), sink2).init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink1.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      sink2.requestOne()
      lastEvents() should ===(Set.empty[TestEvent])

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink1, 1), RequestOne(source)))

      source.onNext(2)
      lastEvents() should ===(Set(OnNext(sink2, 2)))
    }

    "implement non-divergent cycle" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      builder(merge, balance)
        .connect(source, merge.in(0))
        .connect(merge.out, balance.in)
        .connect(balance.out(0), sink)
        .connect(balance.out(1), merge.in(1))
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1), RequestOne(source)))

      // Token enters merge-balance cycle and gets stuck
      source.onNext(2)
      lastEvents() should ===(Set(RequestOne(source)))

      // Unstuck it
      sink.requestOne()
      lastEvents() should ===(Set(OnNext(sink, 2)))

    }

    "implement divergent cycle" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")

      builder(detach, balance, merge)
        .connect(source, merge.in(0))
        .connect(merge.out, balance.in)
        .connect(balance.out(0), sink)
        .connect(balance.out(1), detach.shape.in)
        .connect(detach.shape.out, merge.in(1))
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1), RequestOne(source)))

      // Token enters merge-balance cycle and spins until event limit
      // Without the limit this would spin forever (where forever = Int.MaxValue iterations)
      source.onNext(2, eventLimit = 1000)
      lastEvents() should ===(Set(RequestOne(source)))

      // The cycle is still alive and kicking, just suspended due to the event limit
      interpreter.isSuspended should be(true)

      // Do to the fairness properties of both the interpreter event queue and the balance stage
      // the element will eventually leave the cycle and reaches the sink.
      // This should not hang even though we do not have an event limit set
      sink.requestOne()
      lastEvents() should ===(Set(OnNext(sink, 2)))

      // The cycle is now empty
      interpreter.isSuspended should be(false)
    }

    "release all stage references when finishing an active interpreter" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")
      val identityStage = GraphStages.identity[Int]

      builder(identityStage)
        .connect(source, identityStage.in)
        .connect(identityStage.out, sink)
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      // Leave an element queued so finish() also has to release connection payloads
      source.onNext(1, eventLimit = 0)
      interpreter.isSuspended should be(true)
      lastEvents() should ===(Set.empty[TestEvent])
      interpreter.activeStage should not be null
      interpreter.connections.filter(_ ne null).exists(_.slot != GraphInterpreter.Empty) should be(true)

      val logics = interpreter.logics

      // All logics should be non-null initially
      logics.foreach(logic => logic should not be null)

      // Abort the still-running interpreter and release all stage references
      interpreter.finish()

      // After finish(), all stage logics should have been released (set to null)
      logics.foreach(logic => logic should be(null))
      interpreter.activeStage should be(null)

      // Connection-level references should also be nulled to fully break Connection -> GraphStageLogic chains
      interpreter.connections.filter(_ ne null).foreach { conn =>
        conn.inOwner should be(null)
        conn.outOwner should be(null)
        conn.inHandler should be(null)
        conn.outHandler should be(null)
        conn.slot should be(GraphInterpreter.Empty)
      }

      val snapshot = interpreter.toSnapshot
      snapshot.logics.map(_.label).toSet should ===(Set("<completed>"))
      snapshot.connections should have size interpreter.connections.count(_ ne null)
      snapshot.connections.foreach { connection =>
        connection.in.label should ===("<completed>")
        connection.out.label should ===("<completed>")
      }
    }

    "snapshot connections whose stage ids cannot be resolved" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val sink = new DownstreamProbe[Int]("sink")
      val identityStage = GraphStages.identity[Int]

      builder(identityStage)
        .connect(source, identityStage.in)
        .connect(identityStage.out, sink)
        .init()

      interpreter.connections.head.id = -1

      val connectionSnapshot = interpreter.toSnapshot.connections.head
      connectionSnapshot.in.label should ===("<completed>")
      connectionSnapshot.out.label should ===("<completed>")
    }

    "release references after cascading stage completion" in new TestSetup {
      val source = new UpstreamProbe[Int]("source")
      val detachStage = detacher[Int]
      val identityStage = GraphStages.identity[Int]
      val sink = new DownstreamProbe[Int]("sink")

      builder(detachStage, identityStage)
        .connect(source, detachStage.shape.in)
        .connect(detachStage.shape.out, identityStage.in)
        .connect(identityStage.out, sink)
        .init()

      lastEvents() should ===(Set.empty[TestEvent])

      sink.requestOne()
      lastEvents() should ===(Set(RequestOne(source)))

      val logics = interpreter.logics

      // All logics should be non-null initially
      logics.foreach(logic => logic should not be null)

      source.onNext(1)
      lastEvents() should ===(Set(OnNext(sink, 1), RequestOne(source)))

      // Complete the source - this triggers completion of detacher and identity stages.
      // afterStageHasRun releases logics during normal stage completion (not just in finish()).
      source.onComplete()
      lastEvents() should ===(Set(OnComplete(sink)))
      interpreter.isCompleted should be(true)

      // After normal stage completion via afterStageHasRun, all stage logics should be released
      logics.foreach(logic => logic should be(null))

      // Connection-level references should also be nulled when both sides are finalized
      interpreter.connections.filter(_ ne null).foreach { conn =>
        conn.inOwner should be(null)
        conn.outOwner should be(null)
        conn.inHandler should be(null)
        conn.outHandler should be(null)
        conn.slot should be(GraphInterpreter.Empty)
      }
    }

  }

}
