/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.javadsl

import java.util
import java.util.Comparator

import scala.annotation.unchecked.uncheckedVariance

import org.apache.pekko
import pekko.NotUsed
import pekko.japi.{ function, Pair }
import pekko.stream._
import pekko.stream.scaladsl.GenericGraph
import pekko.util.ConstantFun
import pekko.util.ccompat.JavaConverters._
import pekko.util.unused

/**
 * Merge several streams, taking elements as they arrive from input streams
 * (picking randomly when several have elements ready).
 *
 * '''Emits when''' one of the inputs has an element available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete (eagerComplete=false) or one upstream completes (eagerComplete=true)
 *
 * '''Cancels when''' downstream cancels
 */
object Merge {

  /**
   * Create a new `Merge` operator with the specified output type.
   */
  def create[T](inputPorts: Int): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.Merge(inputPorts)

  /**
   * Create a new `Merge` operator with the specified output type.
   */
  def create[T](@unused clazz: Class[T], inputPorts: Int): Graph[UniformFanInShape[T, T], NotUsed] = create(inputPorts)

  /**
   * Create a new `Merge` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](inputPorts: Int, eagerComplete: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.Merge(inputPorts, eagerComplete = eagerComplete)

  /**
   * Create a new `Merge` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](
      @unused clazz: Class[T],
      inputPorts: Int,
      eagerComplete: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    create(inputPorts, eagerComplete)
}

/**
 * Merge several streams, taking elements as they arrive from input streams
 * (picking from preferred when several have elements ready).
 *
 * '''Emits when''' one of the inputs has an element available, preferring
 * a specified input if multiple have elements available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete (eagerComplete=false) or one upstream completes (eagerComplete=true)
 *
 * '''Cancels when''' downstream cancels
 */
object MergePreferred {

  /**
   * Create a new `MergePreferred` operator with the specified output type.
   */
  def create[T](secondaryPorts: Int): Graph[scaladsl.MergePreferred.MergePreferredShape[T], NotUsed] =
    scaladsl.MergePreferred(secondaryPorts)

  /**
   * Create a new `MergePreferred` operator with the specified output type.
   */
  def create[T](
      @unused clazz: Class[T],
      secondaryPorts: Int): Graph[scaladsl.MergePreferred.MergePreferredShape[T], NotUsed] =
    create(secondaryPorts)

  /**
   * Create a new `MergePreferred` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](
      secondaryPorts: Int,
      eagerComplete: Boolean): Graph[scaladsl.MergePreferred.MergePreferredShape[T], NotUsed] =
    scaladsl.MergePreferred(secondaryPorts, eagerComplete = eagerComplete)

  /**
   * Create a new `MergePreferred` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](
      @unused clazz: Class[T],
      secondaryPorts: Int,
      eagerComplete: Boolean): Graph[scaladsl.MergePreferred.MergePreferredShape[T], NotUsed] =
    create(secondaryPorts, eagerComplete)

}

/**
 * Merge several streams, taking elements as they arrive from input streams
 * (picking from prioritized once when several have elements ready).
 *
 * A `MergePrioritized` has one `out` port, one or more input port with their priorities.
 *
 * '''Emits when''' one of the inputs has an element available, preferring
 * a input based on its priority if multiple have elements available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete (eagerComplete=false) or one upstream completes (eagerComplete=true), default value is `false`
 *
 * '''Cancels when''' downstream cancels
 *
 * A `Broadcast` has one `in` port and 2 or more `out` ports.
 */
object MergePrioritized {

  /**
   * Create a new `MergePrioritized` operator with the specified output type.
   */
  def create[T](priorities: Array[Int]): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.MergePrioritized(priorities.toIndexedSeq)

  /**
   * Create a new `MergePrioritized` operator with the specified output type.
   */
  def create[T](@unused clazz: Class[T], priorities: Array[Int]): Graph[UniformFanInShape[T, T], NotUsed] =
    create(priorities)

  /**
   * Create a new `MergePrioritized` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](priorities: Array[Int], eagerComplete: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.MergePrioritized(priorities.toIndexedSeq, eagerComplete = eagerComplete)

  /**
   * Create a new `MergePrioritized` operator with the specified output type.
   *
   * @param eagerComplete set to true in order to make this operator eagerly
   *                   finish as soon as one of its inputs completes
   */
  def create[T](
      @unused clazz: Class[T],
      priorities: Array[Int],
      eagerComplete: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    create(priorities, eagerComplete)

}

/**
 * Fan-out the stream to several streams. emitting each incoming upstream element to all downstream consumers.
 * It will not shutdown until the subscriptions for at least
 * two downstream subscribers have been established.
 *
 * '''Emits when''' all of the outputs stops backpressuring and there is an input element available
 *
 * '''Backpressures when''' any of the outputs backpressure
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when'''
 *   If eagerCancel is enabled: when any downstream cancels; otherwise: when all downstreams cancel
 */
object Broadcast {

  /**
   * Create a new `Broadcast` operator with the specified input type.
   *
   * @param outputCount number of output ports
   * @param eagerCancel if true, broadcast cancels upstream if any of its downstreams cancel.
   */
  def create[T](outputCount: Int, eagerCancel: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    scaladsl.Broadcast(outputCount, eagerCancel = eagerCancel)

  /**
   * Create a new `Broadcast` operator with the specified input type.
   *
   * @param outputCount number of output ports
   */
  def create[T](outputCount: Int): Graph[UniformFanOutShape[T, T], NotUsed] = create(outputCount, eagerCancel = false)

  /**
   * Create a new `Broadcast` operator with the specified input type.
   */
  def create[T](@unused clazz: Class[T], outputCount: Int): Graph[UniformFanOutShape[T, T], NotUsed] =
    create(outputCount)

}

/**
 * Merge two pre-sorted streams such that the resulting stream is sorted.
 *
 * '''Emits when''' both inputs have an element available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete
 *
 * '''Cancels when''' downstream cancels
 */
object MergeSorted {

  /**
   * Create a new `MergeSorted` operator with the specified input type.
   */
  def create[T <: Comparable[T]](): Graph[FanInShape2[T, T, T], NotUsed] = scaladsl.MergeSorted[T]()

  /**
   * Create a new `MergeSorted` operator with the specified input type.
   */
  def create[T](comparator: Comparator[T]): Graph[FanInShape2[T, T, T], NotUsed] = {
    implicit val ord: Ordering[T] = Ordering.comparatorToOrdering(comparator)
    scaladsl.MergeSorted[T]()
  }

}

/**
 * Takes two streams and passes the first through, the secondary stream is only passed
 * through if the primary stream completes without passing any elements through. When
 * the first element is passed through from the primary the secondary is cancelled.
 * Both incoming streams are materialized when the operator is materialized.
 *
 * On errors the operator is failed regardless of source of the error.
 *
 * '''Emits when''' element is available from primary stream or the primary stream closed without emitting any elements and an element
 *                  is available from the secondary stream
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' the primary stream completes after emitting at least one element, when the primary stream completes
 *                      without emitting and the secondary stream already has completed or when the secondary stream completes
 *
 * '''Cancels when''' downstream cancels
 */
object OrElse {

  /**
   * Create a new `OrElse` operator with the specified input type.
   */
  def create[T](): Graph[UniformFanInShape[T, T], NotUsed] = scaladsl.OrElse[T]()
}

/**
 * Fan-out the stream to two output streams - a 'main' and a 'tap' one. Each incoming element is emitted
 * to the 'main' output; elements are also emitted to the 'tap' output if there is demand;
 * otherwise they are dropped.
 *
 * '''Emits when''' element is available and demand exists from the 'main' output; the element will
 * also be sent to the 'tap' output if there is demand.
 *
 * '''Backpressures when''' the 'main' output backpressures
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when''' the 'main' output cancels
 */
object WireTap {

  /**
   * Create a new `WireTap` operator with the specified output type.
   */
  def create[T](): Graph[FanOutShape2[T, T, T], NotUsed] = scaladsl.WireTap[T]()
}

/**
 * Fan-out the stream to several streams. emitting an incoming upstream element to one downstream consumer according
 * to the partitioner function applied to the element
 *
 * Adheres to the [[ActorAttributes.SupervisionStrategy]] attribute.
 *
 * '''Emits when''' all of the outputs stops backpressuring and there is an input element available
 *
 * '''Backpressures when''' one of the outputs backpressure
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when'''
 *   when any (eagerCancel=true) or all (eagerCancel=false) of the downstreams cancel
 */
object Partition {

  /**
   * Create a new `Partition` operator with the specified input type, `eagerCancel` is `false`.
   *
   * @param outputCount number of output ports
   * @param partitioner function deciding which output each element will be targeted
   */
  def create[T](
      outputCount: Int,
      partitioner: function.Function[T, Integer]): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Partition(outputCount, partitioner.apply, eagerCancel = false)

  /**
   * Create a new `Partition` operator with the specified input type.
   *
   * @param outputCount number of output ports
   * @param partitioner function deciding which output each element will be targeted
   * @param eagerCancel this operator cancels, when any (true) or all (false) of the downstreams cancel
   */
  def create[T](
      outputCount: Int,
      partitioner: function.Function[T, Integer],
      eagerCancel: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Partition(outputCount, partitioner.apply, eagerCancel)

  /**
   * Create a new `Partition` operator with the specified input type, `eagerCancel` is `false`.
   *
   * @param clazz a type hint for this method
   * @param outputCount number of output ports
   * @param partitioner function deciding which output each element will be targeted
   */
  def create[T](
      @unused clazz: Class[T],
      outputCount: Int,
      partitioner: function.Function[T, Integer]): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Partition(outputCount, partitioner.apply, eagerCancel = false)

  /**
   * Create a new `Partition` operator with the specified input type.
   *
   * @param clazz a type hint for this method
   * @param outputCount number of output ports
   * @param partitioner function deciding which output each element will be targeted
   * @param eagerCancel this operator cancels, when any (true) or all (false) of the downstreams cancel
   */
  def create[T](
      @unused clazz: Class[T],
      outputCount: Int,
      partitioner: function.Function[T, Integer],
      eagerCancel: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Partition(outputCount, partitioner.apply, eagerCancel)

}

/**
 * Fan-out the stream to several streams. Each upstream element is emitted to the first available downstream consumer.
 * It will not shutdown until the subscriptions for at least
 * two downstream subscribers have been established.
 *
 * '''Emits when''' any of the outputs stops backpressuring; emits the element to the first available output
 *
 * '''Backpressures when''' all of the outputs backpressure
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when''' If eagerCancel is enabled: when any downstream cancels; otherwise: when all downstreams cancel
 */
object Balance {

  /**
   * Create a new `Balance` operator with the specified input type, `eagerCancel` is `false`.
   *
   * @param outputCount number of output ports
   * @param waitForAllDownstreams if `true` it will not start emitting
   *   elements to downstream outputs until all of them have requested at least one element
   */
  def create[T](outputCount: Int, waitForAllDownstreams: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    scaladsl.Balance(outputCount, waitForAllDownstreams)

  /**
   * Create a new `Balance` operator with the specified input type.
   *
   * @param outputCount number of output ports
   * @param waitForAllDownstreams if `true` it will not start emitting elements to downstream outputs until all of them have requested at least one element
   * @param eagerCancel if true, balance cancels upstream if any of its downstreams cancel, if false, when all have cancelled.
   */
  def create[T](
      outputCount: Int,
      waitForAllDownstreams: Boolean,
      eagerCancel: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Balance(outputCount, waitForAllDownstreams, eagerCancel)

  /**
   * Create a new `Balance` operator with the specified input type, both `waitForAllDownstreams` and `eagerCancel` are `false`.
   *
   * @param outputCount number of output ports
   */
  def create[T](outputCount: Int): Graph[UniformFanOutShape[T, T], NotUsed] =
    create(outputCount, waitForAllDownstreams = false)

  /**
   * Create a new `Balance` operator with the specified input type, both `waitForAllDownstreams` and `eagerCancel` are `false`.
   *
   * @param clazz a type hint for this method
   * @param outputCount number of output ports
   */
  def create[T](@unused clazz: Class[T], outputCount: Int): Graph[UniformFanOutShape[T, T], NotUsed] =
    create(outputCount)

  /**
   * Create a new `Balance` operator with the specified input type, `eagerCancel` is `false`.
   *
   * @param clazz a type hint for this method
   * @param outputCount number of output ports
   * @param waitForAllDownstreams if `true` it will not start emitting elements to downstream outputs until all of them have requested at least one element
   */
  def create[T](
      @unused clazz: Class[T],
      outputCount: Int,
      waitForAllDownstreams: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    create(outputCount, waitForAllDownstreams)

  /**
   * Create a new `Balance` operator with the specified input type.
   *
   * @param clazz a type hint for this method
   * @param outputCount number of output ports
   * @param waitForAllDownstreams if `true` it will not start emitting elements to downstream outputs until all of them have requested at least one element
   * @param eagerCancel if true, balance cancels upstream if any of its downstreams cancel, if false, when all have cancelled.
   */
  def create[T](
      @unused clazz: Class[T],
      outputCount: Int,
      waitForAllDownstreams: Boolean,
      eagerCancel: Boolean): Graph[UniformFanOutShape[T, T], NotUsed] =
    new scaladsl.Balance(outputCount, waitForAllDownstreams, eagerCancel)
}

/**
 * Combine the elements of 2 streams into a stream of tuples.
 *
 * A `Zip` has a `left` and a `right` input port and one `out` port
 *
 * '''Emits when''' all of the inputs has an element available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' any upstream completes
 *
 * '''Cancels when''' downstream cancels
 */
object Zip {
  import pekko.japi.Pair
  import pekko.japi.function.Function2

  /**
   * Create a new `Zip` operator with the specified input types and zipping-function
   * which creates `org.apache.pekko.japi.Pair`s.
   */
  def create[A, B]: Graph[FanInShape2[A, B, A Pair B], NotUsed] =
    ZipWith.create(_toPair.asInstanceOf[Function2[A, B, A Pair B]])

  private[this] final val _toPair: Function2[Any, Any, Any Pair Any] =
    new Function2[Any, Any, Any Pair Any] { override def apply(a: Any, b: Any): Any Pair Any = new Pair(a, b) }
}

/**
 * Combine the elements of 2 streams into a stream of tuples, picking always the latest element of each.
 *
 * A `Zip` has a `left` and a `right` input port and one `out` port
 *
 * '''Emits when''' all of the inputs have at least an element available, and then each time an element becomes
 *                  available on either of the inputs
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' any upstream completes
 *
 * '''Cancels when''' downstream cancels
 */
object ZipLatest {
  import pekko.japi.Pair
  import pekko.japi.function.Function2

  /**
   * Create a new `ZipLatest` operator with the specified input types and zipping-function
   * which creates `org.apache.pekko.japi.Pair`s.
   */
  def create[A, B]: Graph[FanInShape2[A, B, A Pair B], NotUsed] =
    ZipLatestWith.create(_toPair.asInstanceOf[Function2[A, B, A Pair B]])

  private[this] final val _toPair: Function2[Any, Any, Any Pair Any] =
    new Function2[Any, Any, Any Pair Any] { override def apply(a: Any, b: Any): Any Pair Any = new Pair(a, b) }
}

/**
 * Combine the elements of multiple streams into a stream of lists.
 *
 * A `ZipN` has a `n` input ports and one `out` port
 *
 * '''Emits when''' all of the inputs has an element available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' any upstream completes
 *
 * '''Cancels when''' downstream cancels
 */
object ZipN {
  def create[A](n: Int): Graph[UniformFanInShape[A, java.util.List[A]], NotUsed] = {
    ZipWithN.create(ConstantFun.javaIdentityFunction[java.util.List[A]], n)
  }
}

/**
 * Combine the elements of multiple streams into a stream of lists using a combiner function.
 *
 * A `ZipWithN` has a `n` input ports and one `out` port
 *
 * '''Emits when''' all of the inputs has an element available
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' any upstream completes
 *
 * '''Cancels when''' downstream cancels
 */
object ZipWithN {
  def create[A, O](zipper: function.Function[java.util.List[A], O], n: Int): Graph[UniformFanInShape[A, O], NotUsed] = {
    import pekko.util.ccompat.JavaConverters._
    scaladsl.ZipWithN[A, O](seq => zipper.apply(seq.asJava))(n)
  }
}

/**
 * Takes a stream of pair elements and splits each pair to two output streams.
 *
 * An `Unzip` has one `in` port and one `left` and one `right` output port.
 *
 * '''Emits when''' all of the outputs stops backpressuring and there is an input element available
 *
 * '''Backpressures when''' any of the outputs backpressures
 *
 * '''Completes when''' upstream completes
 *
 * '''Cancels when''' any downstream cancels
 */
object Unzip {

  /**
   * Creates a new `Unzip` operator with the specified output types.
   */
  def create[A, B](): Graph[FanOutShape2[A Pair B, A, B], NotUsed] =
    UnzipWith.create(ConstantFun.javaIdentityFunction[Pair[A, B]])

  /**
   * Creates a new `Unzip` operator with the specified output types.
   */
  def create[A, B](@unused left: Class[A], @unused right: Class[B]): Graph[FanOutShape2[A Pair B, A, B], NotUsed] =
    create[A, B]()

}

/**
 * Takes two streams and outputs an output stream formed from the two input streams
 * by consuming one stream first emitting all of its elements, then consuming the
 * second stream emitting all of its elements.
 *
 * '''Emits when''' the current stream has an element available; if the current input completes, it tries the next one
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete
 *
 * '''Cancels when''' downstream cancels
 */
object Concat {

  /**
   * Create a new anonymous `Concat` operator with the specified input types.
   */
  def create[T](): Graph[UniformFanInShape[T, T], NotUsed] = scaladsl.Concat[T]()

  /**
   * Create a new anonymous `Concat` operator with the specified input types.
   */
  def create[T](inputCount: Int): Graph[UniformFanInShape[T, T], NotUsed] = scaladsl.Concat[T](inputCount)

  /**
   * Create a new anonymous `Concat` operator with the specified input types.
   */
  def create[T](inputCount: Int, detachedInputs: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.Concat[T](inputCount, detachedInputs)

  /**
   * Create a new anonymous `Concat` operator with the specified input types.
   */
  def create[T](@unused clazz: Class[T]): Graph[UniformFanInShape[T, T], NotUsed] = create()

}

/**
 * Takes multiple streams whose elements in aggregate have a defined linear
 * sequence with difference 1, starting at 0, and outputs a single stream
 * containing these elements, in order. That is, given a set of input streams
 * with combined elements *e<sub>k</sub>*:
 *
 * *e<sub>0</sub>*, *e<sub>1</sub>*, *e<sub>2</sub>*, ..., *e<sub>n</sub>*
 *
 * This will output a stream ordered by *k*.
 *
 * The elements in the input streams must already be sorted according to the
 * sequence. The input streams do not need to be linear, but the aggregate
 * stream must be linear, no element *k* may be skipped or duplicated, either
 * of these conditions will cause the stream to fail.
 *
 * The typical use case for this is to merge a partitioned stream back
 * together while maintaining order. This can be achieved by first using
 * `zipWithIndex` on the input stream, then partitioning using a
 * [[Partition]] fanout, and then maintaining the index through the processing
 * of each partition before bringing together with this stage.
 *
 * '''Emits when''' one of the upstreams has the next expected element in the
 * sequence available.
 *
 * '''Backpressures when''' downstream backpressures
 *
 * '''Completes when''' all upstreams complete
 *
 * '''Cancels when''' downstream cancels
 */
object MergeSequence {

  /**
   * Create a new anonymous `MergeSequence` operator with two input ports.
   *
   * @param extractSequence The function to extract the sequence from an element.
   */
  def create[T](extractSequence: function.Function[T, Long]): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.MergeSequence[T]()(extractSequence.apply)

  /**
   * Create a new anonymous `MergeSequence` operator.
   *
   * @param inputCount The number of input streams.
   * @param extractSequence The function to extract the sequence from an element.
   */
  def create[T](inputCount: Int, extractSequence: function.Function[T, Long]): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.MergeSequence[T](inputCount)(extractSequence.apply)

  /**
   * Create a new anonymous `Concat` operator with the specified input types.
   *
   * @param clazz a type hint for this method
   * @param inputCount The number of input streams.
   * @param extractSequence The function to extract the sequence from an element.
   */
  def create[T](
      @unused clazz: Class[T],
      inputCount: Int,
      extractSequence: function.Function[T, Long]): Graph[UniformFanInShape[T, T], NotUsed] =
    create(inputCount, extractSequence)

}

object Interleave {

  /**
   * Create a new `Interleave` with the specified number of input ports and given size of elements
   * to take from each input.
   *
   * @param inputPorts number of input ports
   * @param segmentSize number of elements to send downstream before switching to next input port
   * @param eagerClose if true, interleave completes upstream if any of its upstream completes.
   */
  def create[T](
      inputPorts: Int, segmentSize: Int, eagerClose: Boolean): Graph[UniformFanInShape[T, T], NotUsed] =
    scaladsl.Interleave(inputPorts, segmentSize, eagerClose)

  /**
   * Create a new `Interleave` with the specified number of input ports and given size of elements
   * to take from each input, with `eagerClose` set to false.
   *
   * @param inputPorts number of input ports
   * @param segmentSize number of elements to send downstream before switching to next input port
   */
  def create[T](inputPorts: Int, segmentSize: Int): Graph[UniformFanInShape[T, T], NotUsed] =
    create(inputPorts, segmentSize, eagerClose = false)
}

object GraphDSL extends GraphCreate {

  /**
   * Start building a [[GraphDSL]].
   *
   * The [[Builder]] is mutable and not thread-safe,
   * thus you should construct your Graph and then share the constructed immutable [[GraphDSL]].
   */
  def builder[M](): Builder[M] = new Builder()(new scaladsl.GraphDSL.Builder[M])

  /**
   * Creates a new [[Graph]] by importing the given graph list `graphs` and passing their [[Shape]]s
   * along with the [[GraphDSL.Builder]] to the given create function.
   */
  def create[IS <: Shape, S <: Shape, M, G <: Graph[IS, M]](
      graphs: java.util.List[G],
      buildBlock: function.Function2[GraphDSL.Builder[java.util.List[M]], java.util.List[IS], S])
      : Graph[S, java.util.List[M]] = {
    require(!graphs.isEmpty, "The input list must have one or more Graph elements")
    val gbuilder = builder[java.util.List[M]]()
    val toList = (m1: M) => new util.ArrayList(util.Arrays.asList(m1))
    val combine = (s: java.util.List[M], m2: M) => {
      val newList = new util.ArrayList(s)
      newList.add(m2)
      newList
    }
    val sListH = gbuilder.delegate.add(graphs.get(0), toList)
    val sListT = graphs.subList(1, graphs.size()).asScala.map(g => gbuilder.delegate.add(g, combine)).asJava
    val s = buildBlock(gbuilder, {
        val newList = new util.ArrayList[IS]
        newList.add(sListH)
        newList.addAll(sListT)
        newList
      })
    new GenericGraph(s, gbuilder.delegate.result(s))
  }

  final class Builder[+Mat](private[stream] implicit val delegate: scaladsl.GraphDSL.Builder[Mat]) { self =>
    import pekko.stream.scaladsl.GraphDSL.Implicits._

    /**
     * Import a graph into this module, performing a deep copy, discarding its
     * materialized value and returning the copied Ports that are now to be
     * connected.
     */
    def add[S <: Shape](graph: Graph[S, _]): S = delegate.add(graph)

    /**
     * Returns an [[Outlet]] that gives access to the materialized value of this graph. Once the graph is materialized
     * this outlet will emit exactly one element which is the materialized value. It is possible to expose this
     * outlet as an externally accessible outlet of a [[Source]], [[Sink]], [[Flow]] or [[BidiFlow]].
     *
     * It is possible to call this method multiple times to get multiple [[Outlet]] instances if necessary. All of
     * the outlets will emit the materialized value.
     *
     * Be careful to not to feed the result of this outlet to a operator that produces the materialized value itself (for
     * example to a [[Sink#fold]] that contributes to the materialized value) since that might lead to an unresolvable
     * dependency cycle.
     *
     * @return The outlet that will emit the materialized value.
     */
    def materializedValue: Outlet[Mat @uncheckedVariance] = delegate.materializedValue

    def from[T](out: Outlet[T]): ForwardOps[T] = new ForwardOps(out)
    def from[T](src: SourceShape[T]): ForwardOps[T] = new ForwardOps(src.out)
    def from[I, O](f: FlowShape[I, O]): ForwardOps[O] = new ForwardOps(f.out)
    def from[I, O](j: UniformFanInShape[I, O]): ForwardOps[O] = new ForwardOps(j.out)
    def from[I, O](j: UniformFanOutShape[I, O]): ForwardOps[O] = new ForwardOps(findOut(delegate, j, 0))

    def to[T](in: Inlet[T]): ReverseOps[T] = new ReverseOps(in)
    def to[T](dst: SinkShape[T]): ReverseOps[T] = new ReverseOps(dst.in)
    def to[I, O](f: FlowShape[I, O]): ReverseOps[I] = new ReverseOps(f.in)
    def to[I, O](j: UniformFanInShape[I, O]): ReverseOps[I] = new ReverseOps(findIn(delegate, j, 0))
    def to[I, O](j: UniformFanOutShape[I, O]): ReverseOps[I] = new ReverseOps(j.in)

    final class ForwardOps[T](_out: Outlet[T]) {
      def toInlet(in: Inlet[_ >: T]): Builder[Mat] = { _out ~> in; self }
      def to(dst: SinkShape[_ >: T]): Builder[Mat] = { _out ~> dst; self }
      def toFanIn[U](j: UniformFanInShape[_ >: T, U]): Builder[Mat] = { _out ~> j; self }
      def toFanOut[U](j: UniformFanOutShape[_ >: T, U]): Builder[Mat] = { _out ~> j; self }
      def via[U](f: FlowShape[_ >: T, U]): ForwardOps[U] = from((_out ~> f).outlet)
      def viaFanIn[U](j: UniformFanInShape[_ >: T, U]): ForwardOps[U] = from((_out ~> j).outlet)
      def viaFanOut[U](j: UniformFanOutShape[_ >: T, U]): ForwardOps[U] = from((_out ~> j).outlet)
      def out(): Outlet[T] = _out
    }

    final class ReverseOps[T](out: Inlet[T]) {
      def fromOutlet(dst: Outlet[_ <: T]): Builder[Mat] = { out <~ dst; self }
      def from(dst: SourceShape[_ <: T]): Builder[Mat] = { out <~ dst; self }
      def fromFanIn[U](j: UniformFanInShape[U, _ <: T]): Builder[Mat] = { out <~ j; self }
      def fromFanOut[U](j: UniformFanOutShape[U, _ <: T]): Builder[Mat] = { out <~ j; self }
      def via[U](f: FlowShape[U, _ <: T]): ReverseOps[U] = to((out <~ f).inlet)
      def viaFanIn[U](j: UniformFanInShape[U, _ <: T]): ReverseOps[U] = to((out <~ j).inlet)
      def viaFanOut[U](j: UniformFanOutShape[U, _ <: T]): ReverseOps[U] = to((out <~ j).inlet)
    }
  }
}
