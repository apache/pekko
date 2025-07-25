/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2017-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.typed
package internal

import java.time.Duration
import java.util.ArrayList
import java.util.Optional
import java.util.concurrent.CompletionStage
import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.reflect.ClassTag
import scala.util.Try
import scala.annotation.{ nowarn, switch }
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.pekko
import pekko.actor.Address
import pekko.actor.typed.internal.adapter.ActorSystemAdapter
import pekko.annotation.InternalApi
import pekko.dispatch.ExecutionContexts
import pekko.pattern.StatusReply
import pekko.util.BoxedType
import pekko.util.JavaDurationConverters._
import pekko.util.OptionVal
import pekko.util.Timeout

import scala.util.Failure
import scala.util.Success

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ActorContextImpl {

  // single context for logging as there are a few things that are initialized
  // together that we can cache as long as the actor is alive
  object LoggingContext {
    def apply(logger: Logger, tags: Set[String], ctx: ActorContextImpl[_]): LoggingContext = {
      val tagsString =
        // "" means no tags
        if (tags.isEmpty) ""
        else
          // mdc can only contain string values, and we don't want to render that string
          // on each log entry or message, so do that up front here
          tags.mkString(",")

      val pekkoSource = ctx.self.path.toString

      val pekkoAddress =
        ctx.system match {
          case adapter: ActorSystemAdapter[_] => adapter.provider.addressString
          case _                              => Address("pekko", ctx.system.name).toString
        }

      val sourceActorSystem = ctx.system.name

      new LoggingContext(logger, tagsString, pekkoSource, sourceActorSystem, pekkoAddress, hasCustomName = false)
    }
  }

  final case class LoggingContext(
      logger: Logger,
      tagsString: String,
      pekkoSource: String,
      sourceActorSystem: String,
      pekkoAddress: String,
      hasCustomName: Boolean) {
    // toggled once per message if logging is used to avoid having to
    // touch the mdc thread local for cleanup in the end
    var mdcUsed = false

    def withLogger(logger: Logger): LoggingContext = {
      val l = copy(logger = logger, hasCustomName = true)
      l.mdcUsed = mdcUsed
      l
    }
  }

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] trait ActorContextImpl[T]
    extends TypedActorContext[T]
    with javadsl.ActorContext[T]
    with scaladsl.ActorContext[T] {

  import ActorContextImpl.LoggingContext

  // lazily initialized
  private var _logging: OptionVal[LoggingContext] = OptionVal.None

  private var messageAdapterRef: OptionVal[ActorRef[Any]] = OptionVal.None
  private var _messageAdapters: List[(Class[_], Any => T)] = Nil
  private var _timer: OptionVal[TimerSchedulerCrossDslSupport[T]] = OptionVal.None

  // _currentActorThread is on purpose not volatile. Used from `checkCurrentActorThread`.
  // It will always see the right value when accessed from the right thread.
  // Possible that it would NOT detect illegal access sometimes but that's ok.
  private var _currentActorThread: OptionVal[Thread] = OptionVal.None

  // context-shared timer needed to allow for nested timer usage
  def timer: TimerSchedulerCrossDslSupport[T] = _timer match {
    case OptionVal.Some(timer) => timer
    case _                     =>
      checkCurrentActorThread()
      val timer = mkTimer()
      _timer = OptionVal.Some(timer)
      timer
  }

  protected[this] def mkTimer(): TimerSchedulerCrossDslSupport[T] = new TimerSchedulerImpl[T](this)

  override private[pekko] def hasTimer: Boolean = _timer.isDefined

  override private[pekko] def cancelAllTimers(): Unit = {
    if (hasTimer)
      timer.cancelAll()
  }

  override def asJava: javadsl.ActorContext[T] = this

  override def asScala: scaladsl.ActorContext[T] = this

  override def getChild(name: String): Optional[ActorRef[Void]] =
    child(name) match {
      case Some(c) => Optional.of(c.unsafeUpcast[Void])
      case None    => Optional.empty()
    }

  override def getChildren: java.util.List[ActorRef[Void]] = {
    val c = children
    val a = new ArrayList[ActorRef[Void]](c.size)
    val i = c.iterator
    while (i.hasNext) a.add(i.next().unsafeUpcast[Void])
    a
  }

  override def getExecutionContext: ExecutionContextExecutor =
    executionContext

  override def getSelf: pekko.actor.typed.ActorRef[T] =
    self

  override def getSystem: pekko.actor.typed.ActorSystem[Void] =
    system.asInstanceOf[ActorSystem[Void]]

  private def loggingContext(): LoggingContext = {
    // lazy init of logging setup
    _logging match {
      case OptionVal.Some(l) => l
      case _                 =>
        val logClass = LoggerClass.detectLoggerClassFromStack(classOf[Behavior[_]])
        val logger = LoggerFactory.getLogger(logClass.getName)
        val l = LoggingContext(logger, classicActorContext.props.deploy.tags, this)
        _logging = OptionVal.Some(l)
        l
    }
  }

  override def log: Logger = {
    checkCurrentActorThread()
    val logging = loggingContext()
    ActorMdc.setMdc(logging)
    logging.logger
  }

  override def getLog: Logger = log

  override def setLoggerName(name: String): Unit = {
    checkCurrentActorThread()
    _logging = OptionVal.Some(loggingContext().withLogger(LoggerFactory.getLogger(name)))
  }

  override def setLoggerName(clazz: Class[_]): Unit =
    setLoggerName(clazz.getName)

  def hasCustomLoggerName: Boolean = loggingContext().hasCustomName

  // MDC is cleared (if used) from aroundReceive in ActorAdapter after processing each message
  override private[pekko] def clearMdc(): Unit = {
    // avoid access to MDC ThreadLocal if not needed, see details in LoggingContext
    _logging match {
      case OptionVal.Some(ctx) if ctx.mdcUsed =>
        ActorMdc.clearMdc()
        ctx.mdcUsed = false
      case _ =>
    }
  }

  override def setReceiveTimeout(duration: java.time.Duration, msg: T): Unit =
    setReceiveTimeout(duration.asScala, msg)

  override def scheduleOnce[U](delay: java.time.Duration, target: ActorRef[U], msg: U): pekko.actor.Cancellable =
    scheduleOnce(delay.asScala, target, msg)

  override def spawn[U](behavior: pekko.actor.typed.Behavior[U], name: String): pekko.actor.typed.ActorRef[U] =
    spawn(behavior, name, Props.empty)

  override def spawnAnonymous[U](behavior: pekko.actor.typed.Behavior[U]): pekko.actor.typed.ActorRef[U] =
    spawnAnonymous(behavior, Props.empty)

  def delegate(delegator: Behavior[T], msg: T): Behavior[T] = {
    val started = Behavior.start(delegator, this)
    val interpreted = msg match {
      case signal: Signal => Behavior.interpretSignal(started, this, signal)
      case message        => Behavior.interpretMessage(started, this, message)
    }
    (interpreted._tag: @switch) match {
      case BehaviorTags.SameBehavior      => started
      case BehaviorTags.UnhandledBehavior => this.onUnhandled(msg); started
      case _                              => interpreted
    }
  }
  // Scala API impl
  override def ask[Req, Res](target: RecipientRef[Req], createRequest: ActorRef[Res] => Req)(
      mapResponse: Try[Res] => T)(implicit responseTimeout: Timeout, classTag: ClassTag[Res]): Unit = {
    import pekko.actor.typed.scaladsl.AskPattern._
    pipeToSelf(target.ask(createRequest)(responseTimeout, system.scheduler))(mapResponse)
  }

  override def askWithStatus[Req, Res](target: RecipientRef[Req], createRequest: ActorRef[StatusReply[Res]] => Req)(
      mapResponse: Try[Res] => T)(implicit responseTimeout: Timeout, classTag: ClassTag[Res]): Unit =
    ask(target, createRequest) {
      case Success(StatusReply.Success(t: Res)) => mapResponse(Success(t))
      case Success(StatusReply.Error(why))      => mapResponse(Failure(why))
      case fail: Failure[_]                     => mapResponse(fail.asInstanceOf[Failure[Res]])
      case _                                    => throw new RuntimeException() // won't happen, compiler exhaustiveness check pleaser
    }

  // Java API impl
  @nowarn("msg=never used") // resClass is just a pretend param
  override def ask[Req, Res](
      resClass: Class[Res],
      target: RecipientRef[Req],
      responseTimeout: Duration,
      createRequest: pekko.japi.function.Function[ActorRef[Res], Req],
      applyToResponse: pekko.japi.function.Function2[Res, Throwable, T]): Unit = {
    import pekko.actor.typed.javadsl.AskPattern
    pipeToSelf[Res](
      AskPattern.ask(target, ref => createRequest(ref), responseTimeout, system.scheduler),
      applyToResponse)
  }

  override def askWithStatus[Req, Res](
      resClass: Class[Res],
      target: RecipientRef[Req],
      responseTimeout: Duration,
      createRequest: pekko.japi.function.Function[ActorRef[StatusReply[Res]], Req],
      applyToResponse: pekko.japi.function.Function2[Res, Throwable, T]): Unit = {
    implicit val classTag: ClassTag[Res] = ClassTag(resClass)
    ask[Req, StatusReply[Res]](
      classOf[StatusReply[Res]],
      target,
      responseTimeout,
      createRequest,
      (ok: StatusReply[Res], failure: Throwable) =>
        ok match {
          case StatusReply.Success(value: Res) => applyToResponse(value, null)
          case StatusReply.Error(why)          => applyToResponse(null.asInstanceOf[Res], why)
          case null                            => applyToResponse(null.asInstanceOf[Res], failure)
          case _                               => throw new RuntimeException() // won't happen, compiler exhaustiveness check pleaser
        })
  }

  // Scala API impl
  def pipeToSelf[Value](future: Future[Value])(mapResult: Try[Value] => T): Unit = {
    future.onComplete(value => self.unsafeUpcast ! AdaptMessage(value, mapResult))(ExecutionContexts.parasitic)
  }

  // Java API impl
  def pipeToSelf[Value](
      future: CompletionStage[Value],
      applyToResult: pekko.japi.function.Function2[Value, Throwable, T]): Unit = {
    future.handle[Unit] { (value, ex) =>
      if (ex != null)
        self.unsafeUpcast ! AdaptMessage(ex, applyToResult.apply(null.asInstanceOf[Value], _: Throwable))
      else self.unsafeUpcast ! AdaptMessage(value, applyToResult.apply(_: Value, null))
    }
  }

  private[pekko] override def spawnMessageAdapter[U](f: U => T, name: String): ActorRef[U] =
    internalSpawnMessageAdapter(f, name)

  private[pekko] override def spawnMessageAdapter[U](f: U => T): ActorRef[U] =
    internalSpawnMessageAdapter(f, name = "")

  /**
   * INTERNAL API: Needed to make Scala 2.12 compiler happy if spawnMessageAdapter is overloaded for scaladsl/javadsl.
   * Otherwise "ambiguous reference to overloaded definition" because Function is lambda.
   */
  @InternalApi private[pekko] def internalSpawnMessageAdapter[U](f: U => T, name: String): ActorRef[U]

  override def messageAdapter[U: ClassTag](f: U => T): ActorRef[U] = {
    val messageClass = implicitly[ClassTag[U]].runtimeClass.asInstanceOf[Class[U]]
    internalMessageAdapter(messageClass, f)
  }

  override def messageAdapter[U](messageClass: Class[U], f: pekko.japi.function.Function[U, T]): ActorRef[U] =
    internalMessageAdapter(messageClass, f.apply)

  private def internalMessageAdapter[U](messageClass: Class[U], f: U => T): ActorRef[U] = {
    checkCurrentActorThread()
    // replace existing adapter for same class, only one per class is supported to avoid unbounded growth
    // in case "same" adapter is added repeatedly
    val boxedMessageClass = BoxedType(messageClass).asInstanceOf[Class[U]]
    _messageAdapters = (boxedMessageClass, f.asInstanceOf[Any => T]) ::
      _messageAdapters.filterNot { case (cls, _) => cls == boxedMessageClass }
    val ref = messageAdapterRef match {
      case OptionVal.Some(ref) => ref.asInstanceOf[ActorRef[U]]
      case _                   =>
        // AdaptMessage is not really a T, but that is erased
        val ref =
          internalSpawnMessageAdapter[Any](msg => AdaptWithRegisteredMessageAdapter(msg).asInstanceOf[T], "adapter")
        messageAdapterRef = OptionVal.Some(ref)
        ref
    }
    ref.asInstanceOf[ActorRef[U]]
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def messageAdapters: List[(Class[_], Any => T)] = _messageAdapters

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def setCurrentActorThread(): Unit = {
    _currentActorThread match {
      case OptionVal.Some(t) =>
        throw new IllegalStateException(
          s"Invalid access by thread from the outside of $self. " +
          s"Current message is processed by $t, but also accessed from ${Thread.currentThread()}.")
      case _ =>
        _currentActorThread = OptionVal.Some(Thread.currentThread())
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def clearCurrentActorThread(): Unit = {
    _currentActorThread = OptionVal.None
  }

  /**
   * INTERNAL API
   */
  @InternalApi private[pekko] def checkCurrentActorThread(): Unit = {
    val callerThread = Thread.currentThread()
    _currentActorThread match {
      case OptionVal.Some(t) =>
        if (callerThread ne t) {
          throw new UnsupportedOperationException(
            s"Unsupported access to ActorContext operation from the outside of $self. " +
            s"Current message is processed by $t, but ActorContext was called from $callerThread.")
        }
      case _ =>
        throw new UnsupportedOperationException(
          s"Unsupported access to ActorContext from the outside of $self. " +
          s"No message is currently processed by the actor, but ActorContext was called from $callerThread.")
    }
  }
}
