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

package org.apache.pekko.cluster.ddata

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{
  Benchmark,
  BenchmarkMode,
  Fork,
  Level,
  Measurement,
  Mode,
  OutputTimeUnit,
  Param,
  Scope => JmhScope,
  Setup,
  State,
  Warmup
}

import org.apache.pekko
import pekko.actor.Address
import pekko.cluster.UniqueAddress

@Fork(2)
@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 4)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class VersionVectorBenchmark {

  @Param(Array("1", "2", "5"))
  var size = 0

  val nodeA = UniqueAddress(Address("pekko", "Sys", "aaaa", 7355), 1L)
  val nodeB = UniqueAddress(nodeA.address.copy(host = Some("bbbb")), 2L)
  val nodeC = UniqueAddress(nodeA.address.copy(host = Some("cccc")), 3L)
  val nodeD = UniqueAddress(nodeA.address.copy(host = Some("dddd")), 4L)
  val nodeE = UniqueAddress(nodeA.address.copy(host = Some("eeee")), 5L)
  val nodes = Vector(nodeA, nodeB, nodeC, nodeD, nodeE)
  val nodesIndex = Iterator.from(0)
  def nextNode(): UniqueAddress = nodes(nodesIndex.next() % nodes.size)

  var vv1: VersionVector = _
  var vv2: VersionVector = _
  var vv3: VersionVector = _
  var dot1: VersionVector = _

  @Setup(Level.Trial)
  def setup(): Unit = {
    vv1 = (1 to size).foldLeft(VersionVector.empty)((vv, _) => vv + nextNode())
    vv2 = vv1 + nextNode()
    vv3 = vv1 + nextNode()
    dot1 = VersionVector(nodeA, vv1.versionAt(nodeA))
  }

  @Benchmark
  def increment: VersionVector = vv1 + nodeA

  @Benchmark
  def compareSame1: Boolean = vv1 == dot1

  @Benchmark
  def compareSame2: Boolean = vv2 == dot1

  @Benchmark
  def compareGreaterThan1: Boolean = vv1 > dot1

  @Benchmark
  def compareGreaterThan2: Boolean = vv2 > dot1

  @Benchmark
  def merge: VersionVector = vv1.merge(vv2)

  @Benchmark
  def mergeConflicting: VersionVector = vv2.merge(vv3)

}
