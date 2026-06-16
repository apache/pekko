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

package org.apache.pekko.stream.testkit

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.stream.Materializer
import pekko.stream.impl.PhasedFusingActorMaterializer
import pekko.stream.testkit.scaladsl.StreamTestKit.{ assertNoChildren, printDebugDump, stopAllChildren }
import pekko.testkit.PekkoSpec
import pekko.testkit.TestKitUtils

import org.scalatest.Failed

import com.typesafe.config.{ Config, ConfigFactory }

abstract class StreamSpec(_system: ActorSystem) extends PekkoSpec(_system) with StreamConfiguration {

  def this(config: Config) =
    this(
      ActorSystem(
        TestKitUtils.testNameFromCallStack(classOf[StreamSpec], "".r),
        ConfigFactory.load(config.withFallback(PekkoSpec.testConf))))

  def this(s: String) = this(ConfigFactory.parseString(s))

  def this(configMap: Map[String, _]) = this(PekkoSpec.mapToConfig(configMap))

  def this() = this(ActorSystem(TestKitUtils.testNameFromCallStack(classOf[StreamSpec], "".r), PekkoSpec.testConf))

  override def withFixture(test: NoArgTest) = {
    super.withFixture(test) match {
      case failed: Failed =>
        Materializer(_system) match {
          case impl: PhasedFusingActorMaterializer =>
            implicit val ec = impl.system.dispatcher
            println("--- Stream actors debug dump (only works for tests using system materializer) ---")
            printDebugDump(impl.supervisor)
            println("--- Stream actors debug dump end ---")
            stopAllChildren(impl.system, impl.supervisor)
          case _ =>
        }
        failed
      case other =>
        Materializer(_system) match {
          case impl: PhasedFusingActorMaterializer =>
            // Note that this is different from assertAllStages stopped since it tries to
            // *kill* all streams first, before checking if any is stuck. It also does not
            // work for tests starting their own materializers.
            stopAllChildren(impl.system, impl.supervisor)
            val result = test.apply()
            assertNoChildren(impl.system, impl.supervisor,
              Some(FiniteDuration(streamConfig.allStagesStoppedTimeout.millisPart, TimeUnit.MILLISECONDS)))
            result
          case _ => other
        }
    }
  }
}
