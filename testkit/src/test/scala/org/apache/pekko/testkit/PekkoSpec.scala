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

package org.apache.pekko.testkit

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.scalactic.CanEqual
import org.scalactic.TypeCheckedTripleEquals
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Millis
import org.scalatest.time.Span
import org.scalatest.wordspec.AnyWordSpecLike

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.dispatch.Dispatchers
import pekko.event.Logging
import pekko.event.LoggingAdapter
import pekko.testkit.TestEvent._

import java.nio.file.{ Path, Paths }

object PekkoSpec {
  val testConf: Config = ConfigFactory.parseString("""
      pekko {
        loggers = ["org.apache.pekko.testkit.TestEventListener"]
        loglevel = "WARNING"
        stdout-loglevel = "WARNING"
        actor {
          default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = 8
              parallelism-factor = 2.0
              parallelism-max = 8
            }
          }
        }
      }
      """)

  def mapToConfig(map: Map[String, Any]): Config = {
    import pekko.util.ccompat.JavaConverters._
    ConfigFactory.parseMap(map.asJava)
  }

  /**
   * Get resource's normalized path, which works on windows too.
   */
  def resourcePath(resourceName: String): String = {
    val normalizedPath = Paths.get(getClass.getClassLoader.getResource(resourceName).toURI).normalize()
    // Make it works on Windows
    normalizedPath.toString.replace('\\', '/')
  }

  /**
   * Get path's normalized path, which works on Windows too.
   */
  def normalizedPath(path: Path): String = {
    path.toAbsolutePath.normalize().toString.replace('\\', '/')
  }
}

abstract class PekkoSpec(_system: ActorSystem)
    extends TestKit(_system)
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with WatchedByCoroner
    with TypeCheckedTripleEquals
    with ScalaFutures {

  def this(config: Config) =
    this(
      ActorSystem(
        TestKitUtils.testNameFromCallStack(classOf[PekkoSpec], "".r),
        ConfigFactory.load(config.withFallback(PekkoSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, _]) = this(PekkoSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(TestKitUtils.testNameFromCallStack(classOf[PekkoSpec], "".r), PekkoSpec.testConf))

  implicit val patience: PatienceConfig =
    PatienceConfig(testKitSettings.SingleExpectDefaultTimeout.dilated, Span(100, Millis))

  val log: LoggingAdapter = Logging(system, Logging.simpleName(this))

  override val invokeBeforeAllAndAfterAllEvenIfNoTestsAreExpected = true

  final override def beforeAll(): Unit = {
    startCoroner()
    atStartup()
  }

  final override def afterAll(): Unit = {
    beforeTermination()
    shutdown()
    afterTermination()
    stopCoroner()
  }

  protected def atStartup(): Unit = {}

  protected def beforeTermination(): Unit = {}

  protected def afterTermination(): Unit = {}

  def spawn(dispatcherId: String = Dispatchers.DefaultDispatcherId)(body: => Unit): Unit =
    Future(body)(system.dispatchers.lookup(dispatcherId))

  override def expectedTestDuration: FiniteDuration = 60 seconds

  def muteDeadLetters(messageClasses: Class[_]*)(sys: ActorSystem = system): Unit =
    if (!sys.log.isDebugEnabled) {
      def mute(clazz: Class[_]): Unit =
        sys.eventStream.publish(Mute(DeadLettersFilter(clazz)(occurrences = Int.MaxValue)))
      if (messageClasses.isEmpty) mute(classOf[AnyRef])
      else messageClasses.foreach(mute)
    }

  // for ScalaTest === compare of Class objects
  implicit def classEqualityConstraint[A, B]: CanEqual[Class[A], Class[B]] =
    new CanEqual[Class[A], Class[B]] {
      def areEqual(a: Class[A], b: Class[B]) = a == b
    }

  implicit def setEqualityConstraint[A, T <: Set[_ <: A]]: CanEqual[Set[A], T] =
    new CanEqual[Set[A], T] {
      def areEqual(a: Set[A], b: T) = a == b
    }
}
