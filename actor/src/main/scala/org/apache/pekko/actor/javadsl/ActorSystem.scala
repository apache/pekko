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

package org.apache.pekko.actor.javadsl

import java.util.concurrent.CompletionStage
import java.util.Optional

import scala.concurrent.ExecutionContext

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor.ActorSystem.findClassLoader
import pekko.actor._
import pekko.actor.setup.ActorSystemSetup
import pekko.util.FutureConverters._
import pekko.util.OptionConverters._

trait ActorSystem extends org.apache.pekko.actor.ActorSystem {

  /**
   * Asynchronously terminates this actor system by running [[CoordinatedShutdown]] with reason
   * [[CoordinatedShutdown.ActorSystemTerminateReason]].
   *
   * If `pekko.coordinated-shutdown.run-by-actor-system-terminate` is configured to `off`
   * it will not run `CoordinatedShutdown`, but the `ActorSystem` and its actors
   * will still be terminated.
   *
   * This will stop the guardian actor, which in turn
   * will recursively stop all its child actors, and finally the system guardian
   * (below which the logging actors reside) and then execute all registered
   * termination handlers (see [[ActorSystem.registerOnTermination]]).
   * Be careful to not schedule any operations on completion of the returned future
   * using the dispatcher of this actor system as it will have been shut down before the
   * future completes.
   */
  def terminateAsync(): CompletionStage[Terminated] = terminateImpl().asJava

  /**
   * Returns a [[CompletionStage]] which will be completed after the [[ActorSystem]] has been terminated
   * and termination hooks have been executed. If you registered any callback with
   * [[ActorSystem.registerOnTermination]], the returned Future from this method will not complete
   * until all the registered callbacks are finished. Be careful to not schedule any operations,
   * such as `onComplete`, on the dispatchers (`ExecutionContext`) of this actor system as they
   * will have been shut down before this future completes.
   */
  override def getWhenTerminated: CompletionStage[Terminated] = whenTerminatedImpl.asJava
}

object ActorSystem {

  /**
   * Creates a new ActorSystem with the name "default",
   * obtains the current ClassLoader by first inspecting the current threads' getContextClassLoader,
   * then tries to walk the stack to find the callers class loader, then falls back to the ClassLoader
   * associated with the ActorSystem class.
   * Then it loads the default reference configuration using the ClassLoader.
   */
  def create(): ActorSystem = create("default")

  def create(name: String): ActorSystem = create(name, Optional.empty(), Optional.empty(), Optional.empty())

  def create(name: String, setup: ActorSystemSetup): ActorSystem = {
    val bootstrapSettings = setup.get[BootstrapSetup]
    val cl = bootstrapSettings.flatMap(_.classLoader).getOrElse(findClassLoader())
    val appConfig = bootstrapSettings.flatMap(_.config).getOrElse(ConfigFactory.load(cl))
    val defaultEC = bootstrapSettings.flatMap(_.defaultExecutionContext)

    val impl = new ActorSystemImpl(name, appConfig, cl, defaultEC, None, setup) with ActorSystem {
      // TODO: Remove in Pekko 2.0.0, not needed anymore
      override def getWhenTerminated: CompletionStage[Terminated] = super[ActorSystem].getWhenTerminated
    }

    impl.start()
  }

  def create(name: String, bootstrapSetup: BootstrapSetup): ActorSystem =
    create(name, ActorSystemSetup.create(bootstrapSetup))

  /**
   * Creates a new ActorSystem with the specified name, and the specified Config, then
   * obtains the current ClassLoader by first inspecting the current threads' getContextClassLoader,
   * then tries to walk the stack to find the callers class loader, then falls back to the ClassLoader
   * associated with the ActorSystem class.
   *
   * @see <a href="https://lightbend.github.io/config/latest/api/index.html" target="_blank">The Typesafe Config Library API Documentation</a>
   */
  def create(name: String, config: Config): ActorSystem =
    create(name, Optional.of(config), Optional.empty(), Optional.empty())

  /**
   * Creates a new ActorSystem with the specified name, the specified Config, and specified ClassLoader
   *
   * @see <a href="https://lightbend.github.io/config/latest/api/index.html" target="_blank">The Typesafe Config Library API Documentation</a>
   */
  def create(name: String, config: Config, classLoader: ClassLoader): ActorSystem =
    create(name, Optional.of(config), Optional.of(classLoader), Optional.empty())

  /**
   * Creates a new ActorSystem with the specified name,
   * the specified ClassLoader if given, otherwise obtains the current ClassLoader by first inspecting the current
   * threads' getContextClassLoader, then tries to walk the stack to find the callers class loader, then
   * falls back to the ClassLoader associated with the ActorSystem class.
   * If an ExecutionContext is given, it will be used as the default executor inside this ActorSystem.
   * If no ExecutionContext is given, the system will fallback to the executor configured under "pekko.actor.default-dispatcher.default-executor.fallback".
   * The system will use the passed in config, or falls back to the default reference configuration using the ClassLoader.
   *
   * @see <a href="https://lightbend.github.io/config/latest/api/index.html" target="_blank">The Typesafe Config Library API Documentation</a>
   */
  def create(
      name: String,
      config: Optional[Config],
      classLoader: Optional[ClassLoader],
      defaultExecutionContext: Optional[ExecutionContext]): ActorSystem =
    create(name, ActorSystemSetup(BootstrapSetup(classLoader.toScala, config.toScala, defaultExecutionContext.toScala)))
}
