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
import pekko.annotation.InternalApi
import pekko.stream._
import pekko.stream.Attributes._

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object Stages {

  object DefaultAttributes {
    // reusable common attributes
    val IODispatcher = ActorAttributes.IODispatcher
    val inputBufferOne = inputBuffer(initial = 1, max = 1)

    // stage specific default attributes
    val map = name("map")
    val contramap = name("contramap")
    val dimap = name("dimap")
    val log = name("log")
    val filter = name("filter")
    val filterNot = name("filterNot")
    val collect = name("collect")
    val collectFirst = name("collectFirst")
    val collectWhile = name("collectWhile")
    val recover = name("recover")
    val mapError = name("mapError")
    val mapAsync = name("mapAsync")
    val mapAsyncUnordered = name("mapAsyncUnordered")
    val mapAsyncPartition = name("mapAsyncPartition")
    val mapAsyncPartitionUnordered = name("mapAsyncPartitionUnordered")
    val mapWithResource = name("mapWithResource") and IODispatcher
    val ask = name("ask")
    val grouped = name("grouped")
    val groupedAdjacentByWeighted = name("groupedAdjacentByWeighted")
    val groupedWithin = name("groupedWithin")
    val groupedWeighted = name("groupedWeighted")
    val groupedWeightedWithin = name("groupedWeightedWithin")
    val limit = name("limit")
    val limitWeighted = name("limitWeighted")
    val sliding = name("sliding")
    val take = name("take")
    val drop = name("drop")
    val takeWhile = name("takeWhile")
    val dropWhile = name("dropWhile")
    val scan = name("scan")
    val scanAsync = name("scanAsync")
    val fold = name("fold")
    val foldWhile = name("foldWhile")
    val foldAsync = name("foldAsync")
    val reduce = name("reduce")
    val intersperse = name("intersperse")
    val buffer = name("buffer")
    val conflate = name("conflate")
    val batch = name("batch")
    val batchWeighted = name("batchWeighted")
    val expand = name("expand")
    val statefulMap = name("statefulMap")
    val statefulMapConcat = name("statefulMapConcat")
    val mapConcat = name("mapConcat")
    val detacher = name("detacher")
    val groupBy = name("groupBy")
    val prefixAndTail = name("prefixAndTail")
    val flatMapPrefix = name("flatMapPrefix")
    val split = name("split")
    val processor = name("processor")
    val identityOp = name("identityOp")
    val delimiterFraming = name("delimiterFraming")
    val switch = name("switch")

    val initial = name("initial")
    val completion = name("completion")
    val idle = name("idle")
    val idleTimeoutBidi = name("idleTimeoutBidi")
    val delayInitial = name("delayInitial")
    val idleInject = name("idleInject")
    val backpressureTimeout = name("backpressureTimeout")

    val merge = name("merge")
    val mergePreferred = name("mergePreferred")
    val mergePrioritized = name("mergePrioritized")
    val flattenMerge = name("flattenMerge")
    val flattenConcat = name("flattenConcat")
    val recoverWith = name("recoverWith")
    val onErrorComplete = name("onErrorComplete")
    val broadcast = name("broadcast")
    val wireTap = name("wireTap")
    val balance = name("balance")
    val zip = name("zip")
    val zipLatest = name("zipLatest")
    val zipN = name("zipN")
    val zipWithN = name("zipWithN")
    val zipWithIndex = name("zipWithIndex")
    val unzip = name("unzip")
    val concat = name("concat")
    val orElse = name("orElse")
    val repeat = name("repeat")
    val unfold = name("unfold")
    val unfoldAsync = name("unfoldAsync")
    val delay = name("delay")

    val terminationWatcher = name("terminationWatcher")
    val watch = name("watch")

    val publisherSource = name("publisherSource")
    val iterableSource = name("iterableSource")
    val arraySource = name("arraySource")
    val iterateSource = name("iterateSource")
    val cycledSource = name("cycledSource")
    val futureSource = name("futureSource")
    val lazyFutureSource = name("lazyFutureSource")
    val futureFlattenSource = name("futureFlattenSource")
    val tickSource = name("tickSource")
    val singleSource = name("singleSource")
    val lazySingleSource = name("lazySingleSource")
    val emptySource = name("emptySource")
    val maybeSource = name("MaybeSource")
    val neverSource = name("neverSource")
    val failedSource = name("failedSource")
    val subscriberSource = name("subscriberSource")
    val actorRefSource = name("actorRefSource")
    val actorRefWithBackpressureSource = name("actorRefWithBackpressureSource")
    val queueSource = name("queueSource")
    val create = name("create")
    val inputStreamSource = name("inputStreamSource") and IODispatcher
    val outputStreamSource = name("outputStreamSource")
    val fileSource = name("fileSource") and IODispatcher
    val unfoldResourceSource = name("unfoldResourceSource") and IODispatcher
    val unfoldResourceSourceAsync = name("unfoldResourceSourceAsync") and IODispatcher
    val asJavaStream = name("asJavaStream") and IODispatcher
    val javaCollectorParallelUnordered = name("javaCollectorParallelUnordered")
    val javaCollector = name("javaCollector")

    val subscriberSink = name("subscriberSink")
    val cancelledSink = name("cancelledSink")
    val headSink = name("headSink") and inputBufferOne
    val headOptionSink = name("headOptionSink") and inputBufferOne
    val lastSink = name("lastSink")
    val lastOptionSink = name("lastOptionSink")
    val takeLastSink = name("takeLastSink")
    val seqSink = name("seqSink")
    val countSink = name("countSink")
    val publisherSink = name("publisherSink")
    val sourceSink = name("sourceSink")
    val fanoutPublisherSink = name("fanoutPublisherSink")
    val ignoreSink = name("ignoreSink")
    val neverSink = name("neverSink")
    val actorRefSink = name("actorRefSink")
    val actorRefWithBackpressureSink = name("actorRefWithBackpressureSink")
    val queueSink = name("queueSink")
    val lazySink = name("lazySink")
    val lazyFlow = name("lazyFlow")
    val futureFlow = name("futureFlow")
    val lazySource = name("lazySource")
    val outputStreamSink = name("outputStreamSink") and IODispatcher
    val inputStreamSink = name("inputStreamSink")
    val fileSink = name("fileSink") and IODispatcher
    val fromJavaStream = name("fromJavaStream")

    val inputBoundary = name("input-boundary")
    val outputBoundary = name("output-boundary")
    val dropRepeated = name("dropRepeated")
  }

}
