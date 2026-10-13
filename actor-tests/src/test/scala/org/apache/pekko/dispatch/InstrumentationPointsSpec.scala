/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.dispatch

import java.lang.reflect.Method

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the pekko-actor internals that the OpenTelemetry Java agent attaches bytecode advice to.
 *
 * The agent matches these by name and signature, and its muzzle checks do not verify the matchers. When a
 * match stops applying the instrumentation is silently disabled, so this spec fails loudly instead. Keep it
 * in sync with https://github.com/apache/pekko/issues/3472
 */
class InstrumentationPointsSpec extends AnyWordSpec with Matchers {

  private def declaredMethods(className: String): Array[Method] =
    Class.forName(className, false, getClass.getClassLoader).getDeclaredMethods

  private def paramTypeNames(m: Method): Seq[String] = m.getParameterTypes.toIndexedSeq.map(_.getName)

  "The pekko-actor methods instrumented by the OpenTelemetry Java agent" should {

    "include Dispatcher.dispatch(ActorCell, Envelope)" in {
      declaredMethods("org.apache.pekko.dispatch.Dispatcher").filter(_.getName == "dispatch").exists(
        paramTypeNames(_) == Seq(
          "org.apache.pekko.actor.ActorCell",
          "org.apache.pekko.dispatch.Envelope")) shouldBe true
    }

    "include ActorCell.invoke(Envelope)" in {
      declaredMethods("org.apache.pekko.actor.ActorCell").filter(_.getName == "invoke").exists(
        paramTypeNames(_) == Seq("org.apache.pekko.dispatch.Envelope")) shouldBe true
    }

    "include ActorCell.systemInvoke(SystemMessage)" in {
      declaredMethods("org.apache.pekko.actor.ActorCell").filter(_.getName == "systemInvoke").exists(
        paramTypeNames(_) == Seq("org.apache.pekko.dispatch.sysmsg.SystemMessage")) shouldBe true
    }

    "include DefaultSystemMessageQueue.systemEnqueue(ActorRef, SystemMessage)" in {
      declaredMethods("org.apache.pekko.dispatch.DefaultSystemMessageQueue").filter(
        _.getName == "systemEnqueue").exists(
        paramTypeNames(_) == Seq(
          "org.apache.pekko.actor.ActorRef",
          "org.apache.pekko.dispatch.sysmsg.SystemMessage")) shouldBe true
    }

    "include LightArrayRevolverScheduler.schedule taking a Runnable" in {
      declaredMethods("org.apache.pekko.actor.LightArrayRevolverScheduler").filter(
        _.getName == "schedule").exists(
        paramTypeNames(_) == Seq(
          "scala.concurrent.duration.FiniteDuration",
          "scala.concurrent.duration.FiniteDuration",
          "java.lang.Runnable",
          "scala.concurrent.ExecutionContext")) shouldBe true
    }

    "include LightArrayRevolverScheduler.scheduleOnce taking a Runnable" in {
      declaredMethods("org.apache.pekko.actor.LightArrayRevolverScheduler").filter(
        _.getName == "scheduleOnce").exists(
        paramTypeNames(_) == Seq(
          "scala.concurrent.duration.FiniteDuration",
          "java.lang.Runnable",
          "scala.concurrent.ExecutionContext")) shouldBe true
    }
  }
}
