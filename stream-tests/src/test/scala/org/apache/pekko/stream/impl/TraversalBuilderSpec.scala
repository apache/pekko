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

package org.apache.pekko.stream.impl

import org.apache.pekko
import pekko.NotUsed
import pekko.stream._
import pekko.stream.impl.TraversalTestUtils._
import pekko.stream.impl.fusing.IterableSource
import pekko.stream.impl.fusing.GraphStages.{ FutureSource, SingleSource }
import pekko.stream.scaladsl.{ Keep, Source }
import pekko.util.OptionVal
import pekko.testkit.PekkoSpec

import scala.concurrent.Future

class TraversalBuilderSpec extends PekkoSpec {

  "CompositeTraversalBuilder" must {
    val source = new CompositeTestSource
    val sink = new CompositeTestSink
    val flow1 = new CompositeTestFlow("1")
    val flow2 = new CompositeTestFlow("2")

    // ADD closed shape, (and composite closed shape)

    "work with a single Source and Sink" in {
      val builder =
        source.traversalBuilder.add(sink.traversalBuilder, sink.shape, Keep.left).wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(1)

      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(sink.in)
    }

    "work with a nested Source and Sink" in {
      val nestedBuilder =
        TraversalBuilder.empty().add(source.traversalBuilder, source.shape, Keep.left)

      val builder =
        sink.traversalBuilder.add(nestedBuilder, source.shape, Keep.left).wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(1)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(sink.in)
    }

    "work with a remapped Source and Sink" in {
      val remappedShape = SourceShape(Outlet[Any]("remapped.out"))
      remappedShape.out.mappedTo = source.out

      val builder =
        sink.traversalBuilder.add(source.traversalBuilder, remappedShape, Keep.left).wire(remappedShape.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(1)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(sink.in)
    }

    "work with two Flows" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .add(flow2.traversalBuilder, flow2.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, flow2.in)
        .wire(flow2.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(flow1.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow2.in)
      mat.outlets(2) should ===(flow2.out)
      mat.inlets(2) should ===(sink.in)
    }

    "work with two Flows wired in opposite order" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .add(flow2.traversalBuilder, flow2.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(flow2.out, sink.in)
        .wire(flow1.out, flow2.in)
        .wire(source.out, flow1.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(flow1.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow2.in)
      mat.outlets(2) should ===(flow2.out)
      mat.inlets(2) should ===(sink.in)
    }

    "work with two Flows wired in an irregular order" in {
      val builder = source.traversalBuilder
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .add(flow2.traversalBuilder, flow2.shape, Keep.left)
        .wire(flow2.out, sink.in)
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, flow2.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(flow2.out)
      mat.inlets(0) should ===(sink.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow2.in)
      mat.outlets(2) should ===(source.out)
      mat.inlets(2) should ===(flow1.in)
    }

    "work with a Flow wired to its imported self" in {
      val remappedShape = flow1.shape.deepCopy()

      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .add(flow1.traversalBuilder, remappedShape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, remappedShape.in)
        .wire(remappedShape.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(flow1.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow1.in)
      mat.outlets(2) should ===(flow1.out)
      mat.inlets(2) should ===(sink.in)
    }

    "work with a nested Flow chain" in {
      val nestedFlowShape = FlowShape(flow1.in, flow2.out)

      val nestedFlows =
        flow1.traversalBuilder.add(flow2.traversalBuilder, flow2.shape, Keep.left).wire(flow1.out, flow2.in)

      val builder = source.traversalBuilder
        .add(nestedFlows, nestedFlowShape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow2.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(flow1.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow2.in)
      mat.outlets(2) should ===(flow2.out)
      mat.inlets(2) should ===(sink.in)
    }

    "work with a nested Flow chain, imported" in {
      val importedFlowShape = FlowShape(Inlet[Any]("imported.in"), Outlet[Any]("imported.out"))
      importedFlowShape.in.mappedTo = flow1.in
      importedFlowShape.out.mappedTo = flow2.out

      val nestedFlows =
        flow1.traversalBuilder.add(flow2.traversalBuilder, flow2.shape, Keep.left).wire(flow1.out, flow2.in)

      val builder = source.traversalBuilder
        .add(nestedFlows, importedFlowShape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, importedFlowShape.in)
        .wire(importedFlowShape.out, sink.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(3)
      mat.outlets(0) should ===(source.out)
      mat.inlets(0) should ===(flow1.in)
      mat.outlets(1) should ===(flow1.out)
      mat.inlets(1) should ===(flow2.in)
      mat.outlets(2) should ===(flow2.out)
      mat.inlets(2) should ===(sink.in)
    }

    "work with a Flow wired to self" in {
      val builder = flow1.traversalBuilder.wire(flow1.out, flow1.in)

      val mat = testMaterialize(builder)

      mat.connections should ===(1)
      mat.outlets(0) should ===(flow1.out)
      mat.inlets(0) should ===(flow1.in)
    }

    "properly materialize empty builder" in {
      val builder = TraversalBuilder.empty()

      val mat = testMaterialize(builder)
      mat.connections should ===(0)
      mat.outlets.length should ===(0)
      mat.inlets.length should ===(0)
      mat.matValue should ===(NotUsed)
    }

    "properly propagate materialized value with Keep.left" in {
      val builder =
        source.traversalBuilder.add(sink.traversalBuilder, sink.shape, Keep.left).wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("TestSource")
    }

    "keep mapped materialized value of empty builder" in {
      val builder =
        TraversalBuilder
          .empty()
          .transformMat((_: Any) => "NOTUSED")
          .add(source.traversalBuilder, source.shape, Keep.left)
          .add(sink.traversalBuilder, sink.shape, Keep.left)
          .wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("NOTUSED")
    }

    "properly propagate materialized value with Keep.right" in {
      val builder =
        source.traversalBuilder.add(sink.traversalBuilder, sink.shape, Keep.right).wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("TestSink")
    }

    "properly propagate materialized value with Keep.both" in {
      val builder =
        source.traversalBuilder.add(sink.traversalBuilder, sink.shape, Keep.both).wire(source.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===(("TestSource", "TestSink"))
    }

    "properly propagate materialized value with Keep.left with Flow in middle" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("TestSource")
    }

    "properly propagate materialized value with Keep.right with Flow in middle (1)" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.right)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("TestFlow1")
    }

    "properly propagate materialized value with Keep.right with Flow in middle (2)" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.right)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===("TestSink")
    }

    "properly propagate materialized value with Keep.both with Flow in middle (1)" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.both)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===(("TestSource", "TestFlow1"))
    }

    "properly propagate materialized value with Keep.both with Flow in middle (2)" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.both)
        .add(sink.traversalBuilder, sink.shape, Keep.both)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)

      val mat = testMaterialize(builder)

      mat.matValue should ===((("TestSource", "TestFlow1"), "TestSink"))
    }

    "properly map materialized value" in {
      val builder = source.traversalBuilder
        .add(flow1.traversalBuilder, flow1.shape, Keep.right)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)
        .transformMat("MAPPED: " + (_: String))

      val mat = testMaterialize(builder)

      mat.matValue should ===("MAPPED: TestFlow1")
    }

    "properly map materialized value (nested)" in {
      val flowBuilder =
        flow1.traversalBuilder.transformMat("M1: " + (_: String))

      val builder = source.traversalBuilder
        .add(flowBuilder, flow1.shape, Keep.right)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)
        .transformMat("M2: " + (_: String))

      val mat = testMaterialize(builder)

      mat.matValue should ===("M2: M1: TestFlow1")
    }

    "properly set attributes for whole chain" in {
      val builder = source.traversalBuilder
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, sink.in)
        .setAttributes(Attributes.name("test"))

      val mat = testMaterialize(builder)

      mat.attributesAssignments should ===(
        List(
          source -> (Attributes.name("test") and Attributes.name("testSource")),
          sink -> (Attributes.name("test") and Attributes.name("testSink"))))
    }

    "overwrite last attributes until embedded in other builder" in {
      val innerBuilder = source.traversalBuilder
        .add(sink.traversalBuilder.setAttributes(Attributes.name("testSinkB")), sink.shape, Keep.left)
        .wire(source.out, sink.in)
        .setAttributes(Attributes.name("test"))
        .setAttributes(Attributes.name("test2"))

      val builder =
        TraversalBuilder
          .empty()
          .add(innerBuilder, ClosedShape, Keep.left)
          .setAttributes(Attributes.name("outer"))
          .setAttributes(Attributes.name("outer2"))

      val mat = testMaterialize(builder)

      mat.attributesAssignments should ===(
        List(
          source -> (Attributes.name("outer2") and Attributes.name("test2") and Attributes.name("testSource")),
          sink -> (Attributes.name("outer2") and Attributes.name("test2") and Attributes.name("testSinkB"))))
    }

    "propagate attributes to embedded flow" in {
      val flowBuilder =
        flow1.traversalBuilder.setAttributes(Attributes.name("flow"))

      val builder = source.traversalBuilder
        .add(flowBuilder, flow1.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)
        .setAttributes(Attributes.name("test"))

      val mat = testMaterialize(builder)

      mat.attributesAssignments should ===(
        List(
          source -> (Attributes.name("test") and Attributes.name("testSource")),
          flow1 -> (Attributes.name("test") and Attributes.name("flow")),
          sink -> (Attributes.name("test") and Attributes.name("testSink"))))
    }

    "properly track embedded island and its attributes" in {
      val flowBuilder =
        flow1.traversalBuilder.makeIsland(TestIsland1).setAttributes(Attributes.name("flow"))

      val builder = source.traversalBuilder
        .add(flowBuilder, flow1.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)
        .setAttributes(Attributes.name("test"))

      val mat = testMaterialize(builder)

      mat.islandAssignments should ===(
        List(
          (source, Attributes.none, TestDefaultIsland),
          (flow1, Attributes.name("test") and Attributes.name("flow"), TestIsland1),
          (sink, Attributes.none, TestDefaultIsland)))
    }

    "properly ignore redundant island assignment" in {
      val flowBuilder =
        flow1.traversalBuilder.makeIsland(TestIsland1).makeIsland(TestIsland2).setAttributes(Attributes.name("flow"))

      val builder = source.traversalBuilder
        .add(flowBuilder, flow1.shape, Keep.left)
        .add(sink.traversalBuilder, sink.shape, Keep.left)
        .wire(source.out, flow1.in)
        .wire(flow1.out, sink.in)
        .setAttributes(Attributes.name("test"))

      val mat = testMaterialize(builder)

      mat.islandAssignments should ===(
        List(
          (source, Attributes.none, TestDefaultIsland),
          (flow1, Attributes.name("test") and Attributes.name("flow"), TestIsland1),
          (sink, Attributes.none, TestDefaultIsland)))
    }
  }

  "find Source.empty via TraversalBuilder with isEmptySource" in {
    val emptySource = EmptySource
    TraversalBuilder.isEmptySource(emptySource) should be(true)
  }

  "find javadsl Source.empty via TraversalBuilder with isEmptySource" in {
    import pekko.stream.javadsl.Source
    val emptySource = Source.empty()
    TraversalBuilder.isEmptySource(emptySource) should be(true)
  }

  "find scaldsl Source.empty via TraversalBuilder with isEmptySource" in {
    val emptySource = Source.empty
    TraversalBuilder.isEmptySource(emptySource) should be(true)
  }

  "find Source.single via TraversalBuilder" in {
    TraversalBuilder.getSingleSource(Source.single("a")).get.elem should ===("a")
    TraversalBuilder.getSingleSource(Source(List("a", "b"))) should be(OptionVal.None)

    val singleSourceA = new SingleSource("a")
    TraversalBuilder.getSingleSource(singleSourceA) should be(OptionVal.Some(singleSourceA))

    TraversalBuilder.getSingleSource(Source.single("c").async) should be(OptionVal.None)
    TraversalBuilder.getSingleSource(Source.single("d").mapMaterializedValue(_ => "Mat")) should be(OptionVal.None)
  }

  "find Source.single via TraversalBuilder with getValuePresentedSource" in {
    TraversalBuilder.getValuePresentedSource(Source.single("a")).get.asInstanceOf[SingleSource[String]].elem should ===(
      "a")
    val singleSourceA = new SingleSource("a")
    TraversalBuilder.getValuePresentedSource(singleSourceA) should be(OptionVal.Some(singleSourceA))

    TraversalBuilder.getValuePresentedSource(Source.single("c").async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source.single("d").mapMaterializedValue(_ => "Mat")) should be(
      OptionVal.None)
  }

  "find Source.empty via TraversalBuilder with getValuePresentedSource" in {
    val emptySource = EmptySource
    TraversalBuilder.getValuePresentedSource(emptySource) should be(OptionVal.Some(emptySource))

    TraversalBuilder.getValuePresentedSource(Source.empty.async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source.empty.mapMaterializedValue(_ => "Mat")) should be(OptionVal.None)
  }

  "find javadsl Source.empty via TraversalBuilder with getValuePresentedSource" in {
    import pekko.stream.javadsl.Source
    val emptySource = Source.empty()
    TraversalBuilder.getValuePresentedSource(Source.empty()) should be(OptionVal.Some(emptySource))

    TraversalBuilder.getValuePresentedSource(Source.empty().async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source.empty().mapMaterializedValue(_ => "Mat")) should be(OptionVal.None)
  }

  "find Source.future via TraversalBuilder with getValuePresentedSource" in {
    val future = Future.successful("a")
    TraversalBuilder.getValuePresentedSource(Source.future(future)).get.asInstanceOf[FutureSource[
      String]].future should ===(
      future)
    val futureSourceA = new FutureSource(future)
    TraversalBuilder.getValuePresentedSource(futureSourceA) should be(OptionVal.Some(futureSourceA))

    TraversalBuilder.getValuePresentedSource(Source.future(future).async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source.future(future).mapMaterializedValue(_ => "Mat")) should be(
      OptionVal.None)
  }

  "find Source.iterable via TraversalBuilder with getValuePresentedSource" in {
    val iterable = List("a")
    TraversalBuilder.getValuePresentedSource(Source(iterable)).get.asInstanceOf[IterableSource[
      String]].elements should ===(
      iterable)
    val iterableSource = new IterableSource(iterable)
    TraversalBuilder.getValuePresentedSource(iterableSource) should be(OptionVal.Some(iterableSource))

    TraversalBuilder.getValuePresentedSource(Source(iterable).async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source(iterable).mapMaterializedValue(_ => "Mat")) should be(
      OptionVal.None)
  }

  "find Source.javaStreamSource via TraversalBuilder with getValuePresentedSource" in {
    val javaStream = java.util.stream.Stream.empty[String]()
    TraversalBuilder.getValuePresentedSource(Source.fromJavaStream(() => javaStream)).get
      .asInstanceOf[JavaStreamSource[String, _]].open() shouldEqual javaStream
    val streamSource = new JavaStreamSource(() => javaStream)
    TraversalBuilder.getValuePresentedSource(streamSource) should be(OptionVal.Some(streamSource))

    TraversalBuilder.getValuePresentedSource(Source.fromJavaStream(() => javaStream).async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(
      Source.fromJavaStream(() => javaStream).mapMaterializedValue(_ => "Mat")) should be(
      OptionVal.None)
  }

  "find Source.failed via TraversalBuilder with getValuePresentedSource" in {
    val failure = new RuntimeException("failure")
    TraversalBuilder.getValuePresentedSource(Source.failed(failure)).get.asInstanceOf[FailedSource[String]]
      .failure should ===(
      failure)
    val failedSourceA = new FailedSource(failure)
    TraversalBuilder.getValuePresentedSource(failedSourceA) should be(OptionVal.Some(failedSourceA))

    TraversalBuilder.getValuePresentedSource(Source.failed(failure).async) should be(OptionVal.None)
    TraversalBuilder.getValuePresentedSource(Source.failed(failure).mapMaterializedValue(_ => "Mat")) should be(
      OptionVal.None)
  }

}
