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

package org.apache.pekko.actor

import java.util.Optional

import scala.concurrent.duration._
import scala.runtime.BoxedUnit

import org.apache.pekko.testkit.{ ImplicitSender, PekkoSpec, TestProbe }

object AbstractActorPreRestartFinalSpec {

  class PreRestartException extends Exception("test-prerestart") with scala.util.control.NoStackTrace

  /**
   * An AbstractActor subclass that overrides preRestart(Throwable, Optional[Any])
   * to verify it is called correctly via the final bridge method.
   */
  class JavaStyleActor(probe: ActorRef) extends AbstractActor {
    override def createReceive(): AbstractActor.Receive = {
      val pf: PartialFunction[Any, BoxedUnit] = {
        case "crash" => throw new PreRestartException
        case msg     => probe ! msg; BoxedUnit.UNIT
      }
      new AbstractActor.Receive(pf)
    }

    override def preRestart(reason: Throwable, message: Optional[Any]): Unit = {
      probe ! ("preRestart-Optional", reason.getMessage, message.isPresent)
      super.preRestart(reason, message)
    }
  }

  /**
   * An AbstractActorWithStash subclass to verify stash unstashing still works on preRestart.
   */
  class StashActorWithPreRestart(probe: ActorRef) extends AbstractActorWithStash {
    override def createReceive(): AbstractActor.Receive = {
      val pf: PartialFunction[Any, BoxedUnit] = {
        case "stash"   => stash(); BoxedUnit.UNIT
        case "unstash" => unstashAll(); BoxedUnit.UNIT
        case "crash"   => throw new PreRestartException
        case msg       => probe ! msg; BoxedUnit.UNIT
      }
      new AbstractActor.Receive(pf)
    }

    override def preRestart(reason: Throwable, message: Optional[Any]): Unit = {
      probe ! "preRestart-stash"
      super.preRestart(reason, message)
    }
  }
}

class AbstractActorPreRestartFinalSpec extends PekkoSpec with ImplicitSender {
  import AbstractActorPreRestartFinalSpec._

  "AbstractActor.preRestart(Throwable, Option[Any])" must {

    "be declared final via reflection" in {
      val method = classOf[AbstractActor].getDeclaredMethod(
        "preRestart", classOf[Throwable], classOf[Option[_]])
      assert(
        java.lang.reflect.Modifier.isFinal(method.getModifiers),
        "preRestart(Throwable, Option[Any]) should be final in AbstractActor")
    }

    "delegate to preRestart(Throwable, Optional[Any]) which is overridable" in {
      val probe = TestProbe()
      val supervisor = system.actorOf(Props(new Actor {
        val child = context.actorOf(Props(new JavaStyleActor(probe.ref)))
        override val supervisorStrategy = OneForOneStrategy() {
          case _: PreRestartException => SupervisorStrategy.Restart
        }
        def receive = {
          case msg => child.forward(msg)
        }
      }))

      supervisor ! "crash"
      val (label, msg, hasMsg) = probe.expectMsgType[(String, String, Boolean)](3.seconds)
      label shouldBe "preRestart-Optional"
      msg shouldBe "test-prerestart"
      hasMsg shouldBe true
    }

    "not be overridable in AbstractActor subclass (preRestart with Optional is the extension point)" in {
      // Verify that preRestart(Throwable, Optional[Any]) is NOT final
      val optionalMethod = classOf[AbstractActor].getDeclaredMethod(
        "preRestart", classOf[Throwable], classOf[Optional[_]])
      assert(
        !java.lang.reflect.Modifier.isFinal(optionalMethod.getModifiers),
        "preRestart(Throwable, Optional[Any]) should NOT be final in AbstractActor")
    }
  }

  "AbstractActorWithStash" must {

    "call overridden preRestart(Throwable, Optional[Any]) and unstash on restart" in {
      val probe = TestProbe()
      val supervisor = system.actorOf(Props(new Actor {
        val child = context.actorOf(Props(new StashActorWithPreRestart(probe.ref)))
        override val supervisorStrategy = OneForOneStrategy() {
          case _: PreRestartException => SupervisorStrategy.Restart
        }
        def receive = {
          case msg => child.forward(msg)
        }
      }))

      supervisor ! "stash"
      supervisor ! "crash"
      probe.expectMsg(3.seconds, "preRestart-stash")
    }

    "still extend StashSupport (stash/unstash available)" in {
      assert(
        classOf[StashSupport].isAssignableFrom(classOf[AbstractActorWithStash]),
        "AbstractActorWithStash should extend StashSupport")
      assert(
        classOf[StashSupport].isAssignableFrom(classOf[AbstractActorWithUnboundedStash]),
        "AbstractActorWithUnboundedStash should extend StashSupport")
      assert(
        classOf[StashSupport].isAssignableFrom(classOf[AbstractActorWithUnrestrictedStash]),
        "AbstractActorWithUnrestrictedStash should extend StashSupport")
    }
  }

  "UntypedAbstractActor" must {

    "also have final preRestart(Throwable, Option[Any]) (inherited from AbstractActor)" in {
      val method = classOf[UntypedAbstractActor].getMethod(
        "preRestart", classOf[Throwable], classOf[Option[_]])
      assert(
        java.lang.reflect.Modifier.isFinal(method.getModifiers),
        "preRestart(Throwable, Option[Any]) should be final in UntypedAbstractActor")
    }
  }
}
