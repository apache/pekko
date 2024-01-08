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

package org.apache.pekko.stream.impl

import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.immutable
import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.annotation.nowarn
import org.apache.pekko
import pekko.actor._
import pekko.annotation.DoNotInherit
import pekko.annotation.InternalApi
import pekko.dispatch.Dispatchers
import pekko.event.LoggingAdapter
import pekko.pattern.StatusReply
import pekko.stream._
import pekko.stream.impl.fusing.ActorGraphInterpreter
import pekko.stream.impl.fusing.GraphInterpreterShell
import pekko.stream.snapshot.StreamSnapshot
import pekko.util.OptionVal

/**
 * ExtendedActorMaterializer used by subtypes which delegates in-island wiring to [[pekko.stream.impl.PhaseIsland]]s
 *
 * INTERNAL API
 */
@nowarn("msg=deprecated")
@DoNotInherit private[pekko] abstract class ExtendedActorMaterializer extends ActorMaterializer {

  override def withNamePrefix(name: String): ExtendedActorMaterializer

  /** INTERNAL API */
  @InternalApi def materialize[Mat](_runnableGraph: Graph[ClosedShape, Mat]): Mat

  /** INTERNAL API */
  @InternalApi def materialize[Mat](_runnableGraph: Graph[ClosedShape, Mat], defaultAttributes: Attributes): Mat

  /** INTERNAL API */
  @InternalApi private[pekko] def materialize[Mat](
      graph: Graph[ClosedShape, Mat],
      defaultAttributes: Attributes,
      defaultPhase: Phase[Any],
      phases: Map[IslandTag, Phase[Any]]): Mat

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] override def actorOf(context: MaterializationContext, props: Props): ActorRef = {
    val effectiveProps = props.dispatcher match {
      case Dispatchers.DefaultDispatcherId =>
        // the caller said to use the default dispatcher, but that can been trumped by the dispatcher attribute
        props.withDispatcher(context.effectiveAttributes.mandatoryAttribute[ActorAttributes.Dispatcher].dispatcher)
          .withMailbox(PhasedFusingActorMaterializer.MailboxConfigName)
      case _ => props
    }

    actorOf(effectiveProps, context.islandName)
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def actorOf(props: Props, name: String): ActorRef = {
    supervisor match {
      case ref: LocalActorRef =>
        ref.underlying.attachChild(props, name, systemService = false)
      case unknown =>
        throw new IllegalStateException(s"Stream supervisor must be a local actor, was [${unknown.getClass.getName}]")
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] override def logger: LoggingAdapter

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] override def supervisor: ActorRef

}

/**
 * This materializer replaces the default phase with one that will fuse operators into an existing interpreter (via `registerShell`),
 * rather than start a new actor for each of them.
 *
 * The default phases are left in-tact since we still respect `.async` and other tags that were marked within a sub-fused graph.
 */
private[pekko] class SubFusingActorMaterializerImpl(
    val delegate: ExtendedActorMaterializer,
    registerShell: GraphInterpreterShell => ActorRef)
    extends Materializer {
  val subFusingPhase = new Phase[Any] {
    override def apply(
        settings: ActorMaterializerSettings,
        attributes: Attributes,
        materializer: PhasedFusingActorMaterializer,
        islandName: String): PhaseIsland[Any] = {
      new GraphStageIsland(attributes, materializer, islandName, OptionVal(registerShell))
        .asInstanceOf[PhaseIsland[Any]]
    }
  }

  override def executionContext: ExecutionContextExecutor = delegate.executionContext

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat]): Mat =
    delegate match {
      case am: PhasedFusingActorMaterializer =>
        materialize(runnable, am.defaultAttributes)

      case other =>
        throw new IllegalStateException(
          s"SubFusing only supported by [PhasedFusingActorMaterializer], " +
          s"yet was used with [${other.getClass.getName}]!")
    }

  override def materialize[Mat](runnable: Graph[ClosedShape, Mat], defaultAttributes: Attributes): Mat = {
    if (PhasedFusingActorMaterializer.Debug) println(s"Using [${getClass.getSimpleName}] to materialize [${runnable}]")
    val phases = PhasedFusingActorMaterializer.DefaultPhases

    delegate.materialize(runnable, defaultAttributes, subFusingPhase, phases)
  }

  override def scheduleOnce(delay: FiniteDuration, task: Runnable): Cancellable = delegate.scheduleOnce(delay, task)

  override def scheduleWithFixedDelay(
      initialDelay: FiniteDuration,
      delay: FiniteDuration,
      task: Runnable): Cancellable =
    delegate.scheduleWithFixedDelay(initialDelay, delay, task)

  override def scheduleAtFixedRate(
      initialDelay: FiniteDuration,
      interval: FiniteDuration,
      task: Runnable): Cancellable =
    delegate.scheduleAtFixedRate(initialDelay, interval, task)

  override def schedulePeriodically(
      initialDelay: FiniteDuration,
      interval: FiniteDuration,
      task: Runnable): Cancellable =
    scheduleAtFixedRate(initialDelay, interval, task)

  override def withNamePrefix(name: String): SubFusingActorMaterializerImpl =
    new SubFusingActorMaterializerImpl(delegate.withNamePrefix(name), registerShell)

  override def shutdown(): Unit = delegate.shutdown()

  override def isShutdown: Boolean = delegate.isShutdown

  override def system: ActorSystem = delegate.system

  override private[pekko] def logger = delegate.logger

  override private[pekko] def supervisor = delegate.supervisor

  override private[pekko] def actorOf(context: MaterializationContext, props: Props): ActorRef =
    delegate.actorOf(context, props)

  @nowarn("msg=deprecated")
  override def settings: ActorMaterializerSettings = delegate.settings
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object FlowNames extends ExtensionId[FlowNames] with ExtensionIdProvider {
  override def get(system: ActorSystem): FlowNames = super.get(system)
  override def get(system: ClassicActorSystemProvider): FlowNames = super.get(system)
  override def lookup = FlowNames
  override def createExtension(system: ExtendedActorSystem): FlowNames = new FlowNames
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class FlowNames extends Extension {
  val name = SeqActorName("Flow")
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object StreamSupervisor {
  def props(attributes: Attributes, haveShutDown: AtomicBoolean): Props = {
    Props(new StreamSupervisor(haveShutDown))
      .withDeploy(Deploy.local)
      .withDispatcher(attributes.mandatoryAttribute[ActorAttributes.Dispatcher].dispatcher)
      .withMailbox(PhasedFusingActorMaterializer.MailboxConfigName)
  }

  private[stream] val baseName = "StreamSupervisor"
  private val actorName = SeqActorName(baseName)
  def nextName(): String = actorName.next()

  final case class Materialize(props: Props, name: String)
      extends DeadLetterSuppression
      with NoSerializationVerificationNeeded

  final case class GetChildrenSnapshots(timeout: FiniteDuration)
  final case class ChildrenSnapshots(seq: immutable.Seq[StreamSnapshot])
      extends DeadLetterSuppression
      with NoSerializationVerificationNeeded
  private final case class CollectorCompleted(ref: ActorRef)

  /** Testing purpose */
  case object GetChildren

  /** Testing purpose */
  final case class Children(children: Set[ActorRef])

  /** Testing purpose */
  case object StopChildren

  /** Testing purpose */
  case object StoppedChildren
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class StreamSupervisor(haveShutDown: AtomicBoolean) extends Actor {
  import pekko.stream.impl.StreamSupervisor._
  implicit val ec: ExecutionContextExecutor = context.dispatcher
  override def supervisorStrategy: SupervisorStrategy = SupervisorStrategy.stoppingStrategy

  private var snapshotCollectors: Set[ActorRef] = Set.empty

  def receive: Receive = {
    case Materialize(props, name) =>
      val impl = context.actorOf(props, name)
      sender() ! impl
    case GetChildren =>
      sender() ! Children(context.children.toSet)
    case GetChildrenSnapshots(timeout) =>
      val collector =
        context.actorOf(
          SnapshotCollector
            .props(context.children.toSet -- snapshotCollectors, timeout, sender())
            .withDispatcher(context.props.dispatcher))
      context.watchWith(collector, CollectorCompleted(collector))
      snapshotCollectors += collector
    case StopChildren =>
      context.children.foreach(context.stop)
      sender() ! StoppedChildren
    case CollectorCompleted(collector) =>
      snapshotCollectors -= collector
  }

  override def postStop(): Unit = haveShutDown.set(true)
}

@InternalApi
private[pekko] object SnapshotCollector {
  case object SnapshotTimeout
  def props(streamActors: Set[ActorRef], timeout: FiniteDuration, replyTo: ActorRef): Props =
    Props(new SnapshotCollector(streamActors, timeout, replyTo))
}

@InternalApi
private[pekko] final class SnapshotCollector(streamActors: Set[ActorRef], timeout: FiniteDuration, replyTo: ActorRef)
    extends Actor
    with Timers {

  import SnapshotCollector._

  var leftToRespond = streamActors
  var collected: List[StreamSnapshot] = Nil
  if (streamActors.isEmpty) {
    replyTo ! StatusReply.Success(StreamSupervisor.ChildrenSnapshots(Nil))
    context.stop(self)
  } else {
    streamActors.foreach { ref =>
      context.watch(ref)
      ref ! ActorGraphInterpreter.Snapshot
    }
  }

  timers.startSingleTimer(SnapshotTimeout, SnapshotTimeout, timeout)

  override def receive: Receive = {
    case snap: StreamSnapshot =>
      collected = snap :: collected
      leftToRespond -= sender()
      completeIfDone()
    case Terminated(streamActor) =>
      leftToRespond -= streamActor
      completeIfDone()
    case SnapshotTimeout =>
      replyTo ! StatusReply.Error(
        s"Didn't get replies from all stream actors within the timeout of ${timeout.toMillis} ms")
      context.stop(self)
  }

  def completeIfDone(): Unit = {
    if (leftToRespond.isEmpty) {
      replyTo ! StatusReply.Success(StreamSupervisor.ChildrenSnapshots(collected))
      context.stop(self)
    }
  }
}
