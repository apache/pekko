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

package org.apache.pekko.actor

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import org.apache.pekko.testkit.TestProbe

object StashCreationBenchmark {
  class StashingActor extends Actor with Stash {
    def receive = {
      case msg => sender() ! msg
    }
  }

  val props = Props[StashingActor]()
}

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.SampleTime))
@Fork(3)
@Warmup(iterations = 5)
@Measurement(iterations = 10)
class StashCreationBenchmark {
  val conf = ConfigFactory.parseString("""
    my-dispatcher = {
      stash-capacity = 1000
    }
    """)
  implicit val system: ActorSystem = ActorSystem("StashCreationBenchmark", conf)
  val probe = TestProbe()

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    system.terminate()
    Await.ready(system.whenTerminated, 15.seconds)
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def testDefault: Boolean = {
    val stash = system.actorOf(StashCreationBenchmark.props)
    stash.tell("hello", probe.ref)
    probe.expectMsg("hello")
    true
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.MICROSECONDS)
  def testCustom: Boolean = {
    val stash = system.actorOf(StashCreationBenchmark.props.withDispatcher("my-dispatcher"))
    stash.tell("hello", probe.ref)
    probe.expectMsg("hello")
    true
  }
}
