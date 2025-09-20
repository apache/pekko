/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.annotation.nowarn
import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.NonFatal

import org.openjdk.jmh.annotations._

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.ActorAttributes.SupervisionStrategy
import pekko.stream.Attributes.SourceLocation
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.impl.fusing.Collect
import pekko.stream.impl.fusing.Collect.NotApplied
import pekko.stream.scaladsl._
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }

import com.typesafe.config.ConfigFactory

object CollectBenchmark {
  final val OperationsPerInvocation = 10000000
}

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Array(Mode.Throughput))
@nowarn("msg=deprecated")
class CollectBenchmark {
  import CollectBenchmark._

  private val config = ConfigFactory.parseString("""
    pekko.actor.default-dispatcher {
      executor = "fork-join-executor"
      fork-join-executor {
        parallelism-factor = 1
      }
    }
    """)

  private implicit val system: ActorSystem = ActorSystem("CollectBenchmark", config)

  @TearDown
  def shutdown(): Unit = {
    Await.result(system.terminate(), 5.seconds)
  }

  private val newCollect = Source
    .repeat(1)
    .via(new Collect({ case elem => elem }))
    .take(OperationsPerInvocation)
    .toMat(Sink.ignore)(Keep.right)

  private val oldCollect = Source
    .repeat(1)
    .via(new SimpleCollect({ case elem => elem }))
    .take(OperationsPerInvocation)
    .toMat(Sink.ignore)(Keep.right)

  private class SimpleCollect[In, Out](pf: PartialFunction[In, Out])
      extends GraphStage[FlowShape[In, Out]] {
    val in = Inlet[In]("SimpleCollect.in")
    val out = Outlet[Out]("SimpleCollect.out")
    override val shape = FlowShape(in, out)

    override def initialAttributes: Attributes = DefaultAttributes.collect and SourceLocation.forLambda(pf)

    def createLogic(inheritedAttributes: Attributes) =
      new GraphStageLogic(shape) with InHandler with OutHandler {
        private lazy val decider = inheritedAttributes.mandatoryAttribute[SupervisionStrategy].decider
        import Collect.NotApplied

        @nowarn("msg=Any")
        override def onPush(): Unit =
          try {
            pf.applyOrElse(grab(in), NotApplied) match {
              case NotApplied             => pull(in)
              case result: Out @unchecked => push(out, result)
              case _                      => throw new RuntimeException()
            }
          } catch {
            case NonFatal(ex) =>
              decider(ex) match {
                case Supervision.Stop    => failStage(ex)
                case Supervision.Resume  => if (!hasBeenPulled(in)) pull(in)
                case Supervision.Restart => if (!hasBeenPulled(in)) pull(in)
              }
          }

        override def onPull(): Unit = pull(in)

        setHandlers(in, out, this)
      }

    override def toString = "SimpleCollect"
  }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchOldCollect(): Unit =
    Await.result(oldCollect.run(), Duration.Inf)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchNewCollect(): Unit =
    Await.result(newCollect.run(), Duration.Inf)

  @nowarn("msg=Any")
  def collectOnSingleton[A](a: A, pf: PartialFunction[A, A]): A = pf.applyOrElse(a, NotApplied) match {
    case NotApplied => a
    case _          => a
  }

  @nowarn("msg=Any")
  def collectOnSingletonType[A](a: A, pf: PartialFunction[A, A]): A = pf.applyOrElse(a, NotApplied) match {
    case _: NotApplied.type => a
    case _                  => a
  }

  private val string2String: PartialFunction[String, String] = { case a => a }

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchCollectOnSingleton(): Unit =
    collectOnSingleton("", string2String)

  @Benchmark
  @OperationsPerInvocation(OperationsPerInvocation)
  def benchCollectOnSingletonType(): Unit =
    collectOnSingletonType("", string2String)

}
