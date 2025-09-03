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

package org.apache.pekko.stream

import java.util.concurrent.TimeUnit

import scala.concurrent.duration._
import scala.util.control.NoStackTrace

import org.apache.pekko
import pekko.actor.ActorContext
import pekko.actor.ActorRef
import pekko.actor.ActorRefFactory
import pekko.actor.ActorSystem
import pekko.actor.ExtendedActorSystem
import pekko.annotation.InternalApi
import pekko.stream.impl._
import pekko.stream.stage.GraphStageLogic
import pekko.util.Helpers.toRootLowerCase

import com.typesafe.config.Config

@InternalApi
private[pekko] object ActorMaterializer {

  /**
   * Scala API: Creates an ActorMaterializer that can materialize stream blueprints as running streams.
   *
   * The required [[pekko.actor.ActorRefFactory]] (which can be either an [[pekko.actor.ActorSystem]] or an [[pekko.actor.ActorContext]])
   * will be used to create one actor that in turn creates actors for the transformation steps.
   *
   * The materializer's [[pekko.stream.ActorMaterializerSettings]] will be obtained from the
   * configuration of the `context`'s underlying [[pekko.actor.ActorSystem]].
   *
   * The `namePrefix` is used as the first part of the names of the actors running
   * the processing steps. The default `namePrefix` is `"flow"`. The actor names are built up of
   * `namePrefix-flowNumber-flowStepNumber-stepName`.
   */
  @deprecated(
    "Use the system wide materializer with stream attributes or configuration settings to change defaults",
    "Akka 2.6.0")
  def apply(materializerSettings: Option[ActorMaterializerSettings] = None, namePrefix: Option[String] = None)(
      implicit context: ActorRefFactory): Materializer = {
    val system = actorSystemOf(context)

    val settings = materializerSettings.getOrElse(SystemMaterializer(system).materializerSettings)
    apply(settings, namePrefix.getOrElse("flow"))(context)
  }

  /**
   * Scala API: Creates an ActorMaterializer that can materialize stream blueprints as running streams.
   *
   * The required [[pekko.actor.ActorRefFactory]]
   * (which can be either an [[pekko.actor.ActorSystem]] or an [[pekko.actor.ActorContext]])
   * will be used to create these actors, therefore it is *forbidden* to pass this object
   * to another actor if the factory is an ActorContext.
   *
   * The `namePrefix` is used as the first part of the names of the actors running
   * the processing steps. The default `namePrefix` is `"flow"`. The actor names are built up of
   * `namePrefix-flowNumber-flowStepNumber-stepName`.
   */
  @deprecated(
    "Use the system wide materializer with stream attributes or configuration settings to change defaults",
    "Akka 2.6.0")
  def apply(materializerSettings: ActorMaterializerSettings, namePrefix: String)(
      implicit context: ActorRefFactory): Materializer = {

    context match {
      case system: ActorSystem =>
        // system level materializer, defer to the system materializer extension
        SystemMaterializer(system)
          .createAdditionalLegacySystemMaterializer(namePrefix, materializerSettings)
          .asInstanceOf[Materializer]

      case context: ActorContext =>
        // actor context level materializer, will live as a child of this actor
        PhasedFusingActorMaterializer(context, namePrefix, materializerSettings, materializerSettings.toAttributes)

      case other =>
        throw new IllegalArgumentException(s"Unexpected type of context: $other")
    }
  }

  /**
   * Scala API: Creates an ActorMaterializer that can materialize stream blueprints as running streams.
   *
   * The required [[pekko.actor.ActorRefFactory]]
   * (which can be either an [[pekko.actor.ActorSystem]] or an [[pekko.actor.ActorContext]])
   * will be used to create these actors, therefore it is *forbidden* to pass this object
   * to another actor if the factory is an ActorContext.
   *
   * The `namePrefix` is used as the first part of the names of the actors running
   * the processing steps. The default `namePrefix` is `"flow"`. The actor names are built up of
   * `namePrefix-flowNumber-flowStepNumber-stepName`.
   */
  @deprecated(
    "Use the system wide materializer or Materializer.apply(actorContext) with stream attributes or configuration settings to change defaults",
    "Akka 2.6.0")
  def apply(materializerSettings: ActorMaterializerSettings)(implicit context: ActorRefFactory): Materializer =
    apply(Some(materializerSettings), None)

  /**
   * Java API: Creates an ActorMaterializer that can materialize stream blueprints as running streams.
   *
   * The required [[pekko.actor.ActorRefFactory]]
   * (which can be either an [[pekko.actor.ActorSystem]] or an [[pekko.actor.ActorContext]])
   * will be used to create these actors, therefore it is *forbidden* to pass this object
   * to another actor if the factory is an ActorContext.
   *
   * Defaults the actor name prefix used to name actors running the processing steps to `"flow"`.
   * The actor names are built up of `namePrefix-flowNumber-flowStepNumber-stepName`.
   */
  @deprecated(
    "Use the system wide materializer or Materializer.create(actorContext) with stream attributes or configuration settings to change defaults",
    "Akka 2.6.0")
  def create(context: ActorRefFactory): Materializer =
    apply()(context)

  private def actorSystemOf(context: ActorRefFactory): ActorSystem = {
    val system = context match {
      case s: ExtendedActorSystem => s
      case c: ActorContext        => c.system
      case null                   => throw new IllegalArgumentException("ActorRefFactory context must be defined")
      case _                      =>
        throw new IllegalArgumentException(
          s"ActorRefFactory context must be an ActorSystem or ActorContext, got [${context.getClass.getName}]")
    }
    system
  }

}

/**
 * This exception or subtypes thereof should be used to signal materialization
 * failures.
 */
class MaterializationException(msg: String, cause: Throwable = null) extends RuntimeException(msg, cause)

/**
 * A base exception for abrupt stream termination.
 */
sealed class AbruptStreamTerminationException(msg: String, cause: Throwable)
    extends RuntimeException(msg, cause)
    with NoStackTrace

/**
 * This exception signals that an actor implementing a Reactive Streams Subscriber, Publisher or Processor
 * has been terminated without being notified by an onError, onComplete or cancel signal. This usually happens
 * when an ActorSystem is shut down while stream processing actors are still running.
 */
final case class AbruptTerminationException(actor: ActorRef)
    extends AbruptStreamTerminationException(s"Processor actor [$actor] terminated abruptly", cause = null)

/**
 * Signal that the operator was abruptly terminated, usually seen as a call to `postStop` of the `GraphStageLogic` without
 * any of the handler callbacks seeing completion or failure from upstream or cancellation from downstream. This can happen when
 * the actor running the graph is killed, which happens when the materializer or actor system is terminated.
 */
final class AbruptStageTerminationException(logic: GraphStageLogic)
    extends AbruptStreamTerminationException(
      s"GraphStage [$logic] terminated abruptly, caused by for example materializer or actor system termination.",
      cause = null)

@InternalApi // referenced by ArterySettings in pekko-remote and also by some code in pekko-http
private[pekko] object ActorMaterializerSettings {

  /**
   * Create [[ActorMaterializerSettings]] from the settings of an [[pekko.actor.ActorSystem]] (Scala).
   *
   * Prefer using either config for defaults or attributes for per-stream config.
   * See migration guide for details https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html"
   */
  @deprecated(
    "Use config or attributes to configure the materializer. See migration guide for details https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html",
    "Akka 2.6.0")
  def apply(system: ActorSystem): ActorMaterializerSettings =
    apply(system.settings.config.getConfig("pekko.stream.materializer"))

  /**
   * Create [[ActorMaterializerSettings]] from a Config subsection (Scala).
   *
   * Prefer using either config for defaults or attributes for per-stream config.
   * See migration guide for details https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html"
   */
  @deprecated(
    "Use config or attributes to configure the materializer. See migration guide for details https://doc.akka.io/docs/akka/2.6/project/migration-guide-2.5.x-2.6.x.html",
    "Akka 2.6.0")
  def apply(config: Config): ActorMaterializerSettings =
    new ActorMaterializerSettings(
      initialInputBufferSize = config.getInt("initial-input-buffer-size"),
      maxInputBufferSize = config.getInt("max-input-buffer-size"),
      dispatcher = config.getString("dispatcher"),
      supervisionDecider = Supervision.stoppingDecider,
      subscriptionTimeoutSettings = StreamSubscriptionTimeoutSettings(config),
      debugLogging = config.getBoolean("debug-logging"),
      outputBurstLimit = config.getInt("output-burst-limit"),
      fuzzingMode = config.getBoolean("debug.fuzzing-mode"),
      autoFusing = config.getBoolean("auto-fusing"),
      maxFixedBufferSize = config.getInt("max-fixed-buffer-size"),
      syncProcessingLimit = config.getInt("sync-processing-limit"),
      ioSettings = IOSettings(config.getConfig("io")),
      streamRefSettings = StreamRefSettings(config.getConfig("stream-ref")),
      blockingIoDispatcher = config.getString("blocking-io-dispatcher"))
}

/**
 * This class describes the configurable properties of the [[ActorMaterializer]].
 * Please refer to the `withX` methods for descriptions of the individual settings.
 *
 * The constructor is not public API, use create or apply on the [[ActorMaterializerSettings]] companion instead.
 */
final class ActorMaterializerSettings @InternalApi private (
    /*
     * Important note: `initialInputBufferSize`, `maxInputBufferSize`, `dispatcher` and
     * `supervisionDecider` must not be used as values in the materializer, or anything the materializer phases use
     * since these settings allow for overriding using [[Attributes]]. They must always be gotten from the effective
     * attributes.
     */
    private[stream] val initialInputBufferSize: Int,
    private[stream] val maxInputBufferSize: Int,
    private[stream] val dispatcher: String,
    private[stream] val supervisionDecider: Supervision.Decider,
    val subscriptionTimeoutSettings: StreamSubscriptionTimeoutSettings,
    private[stream] val debugLogging: Boolean,
    private[stream] val outputBurstLimit: Int,
    private[stream] val fuzzingMode: Boolean,
    private[stream] val autoFusing: Boolean,
    private[stream] val maxFixedBufferSize: Int,
    private[stream] val syncProcessingLimit: Int,
    val ioSettings: IOSettings,
    val streamRefSettings: StreamRefSettings,
    private[stream] val blockingIoDispatcher: String) {

  require(initialInputBufferSize > 0, "initialInputBufferSize must be > 0")
  require(syncProcessingLimit > 0, "syncProcessingLimit must be > 0")

  requirePowerOfTwo(maxInputBufferSize, "maxInputBufferSize")
  require(
    initialInputBufferSize <= maxInputBufferSize,
    s"initialInputBufferSize($initialInputBufferSize) must be <= maxInputBufferSize($maxInputBufferSize)")

  private def copy(
      initialInputBufferSize: Int = this.initialInputBufferSize,
      maxInputBufferSize: Int = this.maxInputBufferSize,
      dispatcher: String = this.dispatcher,
      supervisionDecider: Supervision.Decider = this.supervisionDecider,
      subscriptionTimeoutSettings: StreamSubscriptionTimeoutSettings = this.subscriptionTimeoutSettings,
      debugLogging: Boolean = this.debugLogging,
      outputBurstLimit: Int = this.outputBurstLimit,
      fuzzingMode: Boolean = this.fuzzingMode,
      autoFusing: Boolean = this.autoFusing,
      maxFixedBufferSize: Int = this.maxFixedBufferSize,
      syncProcessingLimit: Int = this.syncProcessingLimit,
      ioSettings: IOSettings = this.ioSettings,
      streamRefSettings: StreamRefSettings = this.streamRefSettings,
      blockingIoDispatcher: String = this.blockingIoDispatcher) = {
    new ActorMaterializerSettings(
      initialInputBufferSize,
      maxInputBufferSize,
      dispatcher,
      supervisionDecider,
      subscriptionTimeoutSettings,
      debugLogging,
      outputBurstLimit,
      fuzzingMode,
      autoFusing,
      maxFixedBufferSize,
      syncProcessingLimit,
      ioSettings,
      streamRefSettings,
      blockingIoDispatcher)
  }

  /**
   * Each asynchronous piece of a materialized stream topology is executed by one Actor
   * that manages an input buffer for all inlets of its shape. This setting configures
   * the default for initial and maximal input buffer in number of elements for each inlet.
   * This can be overridden for individual parts of the
   * stream topology by using [[pekko.stream.Attributes#inputBuffer]].
   *
   * FIXME: this is used for all kinds of buffers, not only the stream actor, some use initial some use max,
   *        document and or fix if it should not be like that. Search for get[Attributes.InputBuffer] to see how it is used
   */
  @deprecated("Use attribute 'Attributes.InputBuffer' to change setting value", "Akka 2.6.0")
  def withInputBuffer(initialSize: Int, maxSize: Int): ActorMaterializerSettings = {
    if (initialSize == this.initialInputBufferSize && maxSize == this.maxInputBufferSize) this
    else copy(initialInputBufferSize = initialSize, maxInputBufferSize = maxSize)
  }

  /**
   * This setting configures the default dispatcher to be used by streams materialized
   * with the [[ActorMaterializer]]. This can be overridden for individual parts of the
   * stream topology by using [[pekko.stream.Attributes#dispatcher]].
   */
  @deprecated("Use attribute 'ActorAttributes.Dispatcher' to change setting value", "Akka 2.6.0")
  def withDispatcher(dispatcher: String): ActorMaterializerSettings = {
    if (this.dispatcher == dispatcher) this
    else copy(dispatcher = dispatcher)
  }

  /**
   * Leaked publishers and subscribers are cleaned up when they are not used within a given
   * deadline, configured by [[StreamSubscriptionTimeoutSettings]].
   */
  def withSubscriptionTimeoutSettings(settings: StreamSubscriptionTimeoutSettings): ActorMaterializerSettings =
    if (settings == this.subscriptionTimeoutSettings) this
    else copy(subscriptionTimeoutSettings = settings)

  def withIOSettings(ioSettings: IOSettings): ActorMaterializerSettings =
    if (ioSettings == this.ioSettings) this
    else copy(ioSettings = ioSettings)

  /** Change settings specific to [[SourceRef]] and [[SinkRef]]. */
  def withStreamRefSettings(streamRefSettings: StreamRefSettings): ActorMaterializerSettings =
    if (streamRefSettings == this.streamRefSettings) this
    else copy(streamRefSettings = streamRefSettings)

  private def requirePowerOfTwo(n: Integer, name: String): Unit = {
    require(n > 0, s"$name must be > 0")
    require((n & (n - 1)) == 0, s"$name must be a power of two")
  }

  override def equals(other: Any): Boolean = other match {
    case s: ActorMaterializerSettings =>
      s.initialInputBufferSize == initialInputBufferSize &&
      s.maxInputBufferSize == maxInputBufferSize &&
      s.dispatcher == dispatcher &&
      s.supervisionDecider == supervisionDecider &&
      s.subscriptionTimeoutSettings == subscriptionTimeoutSettings &&
      s.debugLogging == debugLogging &&
      s.outputBurstLimit == outputBurstLimit &&
      s.syncProcessingLimit == syncProcessingLimit &&
      s.fuzzingMode == fuzzingMode &&
      s.autoFusing == autoFusing &&
      s.ioSettings == ioSettings &&
      s.blockingIoDispatcher == blockingIoDispatcher
    case _ => false
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  private[pekko] def toAttributes: Attributes =
    Attributes(
      // these are the core stream/materializer settings, ad hoc handling of defaults for the stage specific ones
      // for stream refs and io live with the respective stages
      Attributes.InputBuffer(initialInputBufferSize, maxInputBufferSize) ::
      Attributes.CancellationStrategy.Default :: // FIXME: make configurable, see https://github.com/akka/akka/issues/28000
      Attributes.NestedMaterializationCancellationPolicy.Default ::
      ActorAttributes.Dispatcher(dispatcher) ::
      ActorAttributes.SupervisionStrategy(supervisionDecider) ::
      ActorAttributes.DebugLogging(debugLogging) ::
      ActorAttributes
        .StreamSubscriptionTimeout(subscriptionTimeoutSettings.timeout, subscriptionTimeoutSettings.mode) ::
      ActorAttributes.OutputBurstLimit(outputBurstLimit) ::
      ActorAttributes.FuzzingMode(fuzzingMode) ::
      ActorAttributes.MaxFixedBufferSize(maxFixedBufferSize) ::
      ActorAttributes.SyncProcessingLimit(syncProcessingLimit) ::

      Nil)

  override def toString: String =
    s"ActorMaterializerSettings($initialInputBufferSize,$maxInputBufferSize," +
    s"$dispatcher,$supervisionDecider,$subscriptionTimeoutSettings,$debugLogging,$outputBurstLimit," +
    s"$syncProcessingLimit,$fuzzingMode,$autoFusing,$ioSettings)"
}

@InternalApi
private[pekko] object IOSettings {
  @deprecated(
    "Use setting 'pekko.stream.materializer.io.tcp.write-buffer-size' or attribute TcpAttributes.writeBufferSize instead",
    "Akka 2.6.0")
  def apply(config: Config): IOSettings =
    new IOSettings(
      tcpWriteBufferSize = math.min(Int.MaxValue, config.getBytes("tcp.write-buffer-size")).toInt,
      coalesceWrites = config.getInt("tcp.coalesce-writes"))
}

final class IOSettings private (
    private[stream] val tcpWriteBufferSize: Int,
    val coalesceWrites: Int) {

  // constructor for binary compatibility with version 2.6.15 and earlier
  @deprecated("Use attribute 'TcpAttributes.TcpWriteBufferSize' to read the concrete setting value", "Akka 2.6.0")
  def this(tcpWriteBufferSize: Int) = this(tcpWriteBufferSize, coalesceWrites = 10)

  def withTcpWriteBufferSize(value: Int): IOSettings = copy(tcpWriteBufferSize = value)

  def withCoalesceWrites(value: Int): IOSettings = copy(coalesceWrites = value)

  private def copy(tcpWriteBufferSize: Int = tcpWriteBufferSize, coalesceWrites: Int = coalesceWrites): IOSettings =
    new IOSettings(tcpWriteBufferSize, coalesceWrites)

  override def equals(other: Any): Boolean = other match {
    case s: IOSettings => s.tcpWriteBufferSize == tcpWriteBufferSize && s.coalesceWrites == coalesceWrites
    case _             => false
  }

  override def hashCode(): Int =
    31 * tcpWriteBufferSize + coalesceWrites

  override def toString =
    s"""IoSettings($tcpWriteBufferSize,$coalesceWrites)"""
}

object StreamSubscriptionTimeoutSettings {
  import pekko.stream.StreamSubscriptionTimeoutTerminationMode._

  /**
   * Create settings from individual values (Java).
   */
  def create(
      mode: StreamSubscriptionTimeoutTerminationMode,
      timeout: FiniteDuration): StreamSubscriptionTimeoutSettings =
    new StreamSubscriptionTimeoutSettings(mode, timeout)

  /**
   * Create settings from individual values (Scala).
   */
  def apply(
      mode: StreamSubscriptionTimeoutTerminationMode,
      timeout: FiniteDuration): StreamSubscriptionTimeoutSettings =
    new StreamSubscriptionTimeoutSettings(mode, timeout)

  /**
   * Create settings from a Config subsection (Java).
   */
  def create(config: Config): StreamSubscriptionTimeoutSettings =
    apply(config)

  /**
   * Create settings from a Config subsection (Scala).
   */
  def apply(config: Config): StreamSubscriptionTimeoutSettings = {
    val c = config.getConfig("subscription-timeout")
    StreamSubscriptionTimeoutSettings(
      mode = toRootLowerCase(c.getString("mode")) match {
        case "no" | "off" | "false" | "noop" => NoopTermination
        case "warn"                          => WarnTermination
        case "cancel"                        => CancelTermination
      }, timeout = c.getDuration("timeout", TimeUnit.MILLISECONDS).millis)
  }
}

/**
 * Leaked publishers and subscribers are cleaned up when they are not used within a given
 * deadline, configured by [[StreamSubscriptionTimeoutSettings]].
 */
final class StreamSubscriptionTimeoutSettings(
    private[stream] val mode: StreamSubscriptionTimeoutTerminationMode,
    private[stream] val timeout: FiniteDuration) {
  override def equals(other: Any): Boolean = other match {
    case s: StreamSubscriptionTimeoutSettings => s.mode == mode && s.timeout == timeout
    case _                                    => false
  }
  override def toString: String = s"StreamSubscriptionTimeoutSettings($mode,$timeout)"
}

/**
 * This mode describes what shall happen when the subscription timeout expires for
 * substream Publishers created by operations like `prefixAndTail`.
 */
sealed abstract class StreamSubscriptionTimeoutTerminationMode

object StreamSubscriptionTimeoutTerminationMode {
  case object NoopTermination extends StreamSubscriptionTimeoutTerminationMode
  case object WarnTermination extends StreamSubscriptionTimeoutTerminationMode
  case object CancelTermination extends StreamSubscriptionTimeoutTerminationMode

  /**
   * Do not do anything when timeout expires.
   */
  def noop: StreamSubscriptionTimeoutTerminationMode = NoopTermination

  /**
   * Log a warning when the timeout expires.
   */
  def warn: StreamSubscriptionTimeoutTerminationMode = WarnTermination

  /**
   * When the timeout expires attach a Subscriber that will immediately cancel its subscription.
   */
  def cancel: StreamSubscriptionTimeoutTerminationMode = CancelTermination

}
