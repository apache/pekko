/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor.testkit.typed.scaladsl

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.actor.DeadLetter
import pekko.actor.Dropped
import pekko.actor.UnhandledMessage
import pekko.actor.testkit.typed.TestKitSettings
import pekko.actor.testkit.typed.internal.TestKitUtils
import pekko.actor.typed.ActorRef
import pekko.actor.typed.ActorSystem
import pekko.actor.typed.Behavior
import pekko.actor.typed.Props
import pekko.util.Timeout

object ActorTestKitBase {
  def testNameFromCallStack(): String = TestKitUtils.testNameFromCallStack(classOf[ActorTestKitBase])
}

/**
 * A base class for the [[ActorTestKit]], making it possible to have testing framework (e.g. ScalaTest)
 * manage the lifecycle of the testkit.
 *
 * An implementation for ScalaTest is [[ScalaTestWithActorTestKit]].
 *
 * Another abstract class that is testing framework specific should extend this class and
 * automatically shut down the `testKit` when the test completes or fails by implementing [[ActorTestKitBase#afterAll]].
 */
abstract class ActorTestKitBase(val testKit: ActorTestKit) {

  def this() = this(ActorTestKit(ActorTestKitBase.testNameFromCallStack()))

  /**
   * Use a custom config for the actor system.
   */
  def this(config: String) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), ConfigFactory.parseString(config)))

  /**
   * Use a custom config for the actor system.
   */
  def this(config: Config) = this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config))

  /**
   * Use a custom config for the actor system, and a custom [[pekko.actor.testkit.typed.TestKitSettings]].
   */
  def this(config: Config, settings: TestKitSettings) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config, settings))

  // delegates of the TestKit api for minimum fuss
  /**
   * See corresponding method on [[ActorTestKit]]
   */
  implicit def system: ActorSystem[Nothing] = testKit.system

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  implicit def testKitSettings: TestKitSettings = testKit.testKitSettings

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  implicit def timeout: Timeout = testKit.timeout

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def spawn[T](behavior: Behavior[T]): ActorRef[T] = testKit.spawn(behavior)

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def spawn[T](behavior: Behavior[T], name: String): ActorRef[T] = testKit.spawn(behavior, name)

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def spawn[T](behavior: Behavior[T], props: Props): ActorRef[T] = testKit.spawn(behavior, props)

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def spawn[T](behavior: Behavior[T], name: String, props: Props): ActorRef[T] = testKit.spawn(behavior, name, props)

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def createTestProbe[M](): TestProbe[M] = testKit.createTestProbe[M]()

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def createTestProbe[M](name: String): TestProbe[M] = testKit.createTestProbe(name)

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def createDroppedMessageProbe(): TestProbe[Dropped] = testKit.createDroppedMessageProbe()

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def createDeadLetterProbe(): TestProbe[DeadLetter] = testKit.createDeadLetterProbe()

  /**
   * See corresponding method on [[ActorTestKit]]
   */
  def createUnhandledMessageProbe(): TestProbe[UnhandledMessage] = testKit.createUnhandledMessageProbe()

  /**
   * Additional testing utilities for serialization.
   */
  def serializationTestKit: SerializationTestKit = testKit.serializationTestKit

  /**
   * To be implemented by "more" concrete class that can mixin `BeforeAndAfterAll` or similar,
   * for example `FlatSpecLike with BeforeAndAfterAll`. Implement by calling
   * `testKit.shutdownTestKit()`.
   */
  protected def afterAll(): Unit

}
