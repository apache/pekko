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

package org.apache.pekko.actor

import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.apache.pekko
import pekko.ConfigurationException
import pekko.dispatch._
import pekko.testkit._
import pekko.util.Helpers.ConfigOps
import pekko.util.unused

object ActorMailboxSpec {
  val mailboxConf = ConfigFactory.parseString(s"""
    unbounded-dispatcher {
      mailbox-type = "org.apache.pekko.dispatch.UnboundedMailbox"
    }

    bounded-dispatcher {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 10s
      mailbox-type = "org.apache.pekko.dispatch.BoundedMailbox"
    }

    requiring-bounded-dispatcher {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 10s
      mailbox-type = "org.apache.pekko.dispatch.BoundedMailbox"
      mailbox-requirement = "org.apache.pekko.dispatch.BoundedMessageQueueSemantics"
    }

    balancing-dispatcher {
      type = "org.apache.pekko.dispatch.BalancingDispatcherConfigurator"
    }

    balancing-bounded-dispatcher {
      type = "org.apache.pekko.dispatch.BalancingDispatcherConfigurator"
      mailbox-push-timeout-time = 10s
      mailbox-capacity = 1000
      mailbox-type = "org.apache.pekko.dispatch.BoundedMailbox"
    }

    requiring-balancing-bounded-dispatcher {
      type = "org.apache.pekko.dispatch.BalancingDispatcherConfigurator"
      mailbox-requirement = "org.apache.pekko.actor.ActorMailboxSpec$$MCBoundedMessageQueueSemantics"
    }

    unbounded-mailbox {
      mailbox-type = "org.apache.pekko.dispatch.UnboundedMailbox"
    }

    bounded-mailbox {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 10s
      mailbox-type = "org.apache.pekko.dispatch.BoundedMailbox"
    }

    bounded-mailbox-with-zero-pushtimeout {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 0s
      mailbox-type = "org.apache.pekko.dispatch.BoundedMailbox"
    }

    bounded-control-aware-mailbox {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 10s
      mailbox-type = "org.apache.pekko.dispatch.BoundedControlAwareMailbox"
    }

    unbounded-control-aware-mailbox {
      mailbox-type = "org.apache.pekko.dispatch.UnboundedControlAwareMailbox"
    }


    mc-bounded-mailbox {
      mailbox-capacity = 1000
      mailbox-push-timeout-time = 10s
      mailbox-type = "org.apache.pekko.actor.ActorMailboxSpec$$MCBoundedMailbox"
    }

    pekko.actor.deployment {
      /default-default {
      }
      /default-override-from-props {
      }
      /default-override-from-trait {
      }
      /default-override-from-trait-bounded-control-aware {
      }
      /default-override-from-trait-unbounded-control-aware {
      }
      /default-override-from-stash {
      }
      /default-bounded {
        mailbox = bounded-mailbox
      }
      /default-bounded-control-aware {
        mailbox = bounded-control-aware-mailbox
      }
      /default-unbounded-control-aware {
        mailbox = unbounded-control-aware-mailbox
      }
      /default-bounded-mailbox-with-zero-pushtimeout {
        mailbox = bounded-mailbox-with-zero-pushtimeout
      }
      /default-unbounded-deque {
        mailbox = pekko.actor.mailbox.unbounded-deque-based
      }
      /default-unbounded-deque-override-trait {
        mailbox = pekko.actor.mailbox.unbounded-deque-based
      }
      /unbounded-default {
        dispatcher = unbounded-dispatcher
      }
      /unbounded-default-override-trait {
        dispatcher = unbounded-dispatcher
      }
      /unbounded-bounded {
        dispatcher= unbounded-dispatcher
        mailbox = bounded-mailbox
      }
      /bounded-default {
        dispatcher = bounded-dispatcher
      }
      /bounded-unbounded {
        dispatcher = bounded-dispatcher
        mailbox = unbounded-mailbox
      }
      /bounded-unbounded-override-props {
        dispatcher = bounded-dispatcher
        mailbox = unbounded-mailbox
      }
      /bounded-deque-requirements-configured {
        dispatcher = requiring-bounded-dispatcher
        mailbox = pekko.actor.mailbox.bounded-deque-based
      }
      /bounded-deque-require-unbounded-configured {
        dispatcher = requiring-bounded-dispatcher
        mailbox = pekko.actor.mailbox.unbounded-deque-based
      }
      /bounded-deque-require-unbounded-unconfigured {
        dispatcher = requiring-bounded-dispatcher
      }
      /bounded-deque-requirements-configured-props-disp {
        mailbox = pekko.actor.mailbox.bounded-deque-based
      }
      /bounded-deque-require-unbounded-configured-props-disp {
        mailbox = pekko.actor.mailbox.unbounded-deque-based
      }
      /bounded-deque-requirements-configured-props-mail {
        dispatcher = requiring-bounded-dispatcher
      }
      /bounded-deque-require-unbounded-configured-props-mail {
        dispatcher = requiring-bounded-dispatcher
      }
      /bounded-deque-require-unbounded-unconfigured-props-mail {
        dispatcher = requiring-bounded-dispatcher
      }
    }

    pekko.actor.mailbox.requirements {
      "org.apache.pekko.actor.ActorMailboxSpec$$MCBoundedMessageQueueSemantics" =
        mc-bounded-mailbox
    }
                                              """)

  class QueueReportingActor extends Actor {
    def receive = {
      case _ => sender() ! context.asInstanceOf[ActorCell].mailbox.messageQueue
    }
  }

  class BoundedQueueReportingActor extends QueueReportingActor with RequiresMessageQueue[BoundedMessageQueueSemantics]

  class BoundedControlAwareQueueReportingActor
      extends QueueReportingActor
      with RequiresMessageQueue[BoundedControlAwareMessageQueueSemantics]

  class UnboundedControlAwareQueueReportingActor
      extends QueueReportingActor
      with RequiresMessageQueue[UnboundedControlAwareMessageQueueSemantics]

  class StashQueueReportingActor extends QueueReportingActor with Stash

  class StashQueueReportingActorWithParams(@unused i: Int, @unused s: String) extends StashQueueReportingActor

  val UnboundedMailboxTypes = Seq(classOf[UnboundedMessageQueueSemantics])
  val BoundedMailboxTypes = Seq(classOf[BoundedMessageQueueSemantics])

  val UnboundedDeqMailboxTypes = Seq(
    classOf[DequeBasedMessageQueueSemantics],
    classOf[UnboundedMessageQueueSemantics],
    classOf[UnboundedDequeBasedMessageQueueSemantics])

  val BoundedDeqMailboxTypes = Seq(
    classOf[DequeBasedMessageQueueSemantics],
    classOf[BoundedMessageQueueSemantics],
    classOf[BoundedDequeBasedMessageQueueSemantics])

  val BoundedControlAwareMailboxTypes = Seq(
    classOf[BoundedMessageQueueSemantics],
    classOf[ControlAwareMessageQueueSemantics],
    classOf[BoundedControlAwareMessageQueueSemantics])
  val UnboundedControlAwareMailboxTypes = Seq(
    classOf[UnboundedMessageQueueSemantics],
    classOf[ControlAwareMessageQueueSemantics],
    classOf[UnboundedControlAwareMessageQueueSemantics])

  trait MCBoundedMessageQueueSemantics extends MessageQueue with MultipleConsumerSemantics
  final case class MCBoundedMailbox(capacity: Int, pushTimeOut: FiniteDuration)
      extends MailboxType
      with ProducesMessageQueue[MCBoundedMessageQueueSemantics] {

    def this(settings: ActorSystem.Settings, config: Config) =
      this(config.getInt("mailbox-capacity"), config.getNanosDuration("mailbox-push-timeout-time"))

    override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
      new BoundedMailbox.MessageQueue(capacity, pushTimeOut)
  }

}

class ActorMailboxSpec(conf: Config) extends PekkoSpec(conf) with DefaultTimeout with ImplicitSender {

  import ActorMailboxSpec._

  def this() = this(ActorMailboxSpec.mailboxConf)

  def checkMailboxQueue(props: Props, name: String, types: Seq[Class[_]]): MessageQueue = {
    val actor = system.actorOf(props, name)

    actor ! "ping"
    val q = expectMsgType[MessageQueue]
    types.foreach(t => assert(t.isInstance(q), s"Type [${q.getClass.getName}] is not assignable to [${t.getName}]"))
    q
  }

  "An Actor" must {

    "get an unbounded message queue by default" in {
      checkMailboxQueue(Props[QueueReportingActor](), "default-default", UnboundedMailboxTypes)
    }

    "get an unbounded deque message queue when it is only configured on the props" in {
      checkMailboxQueue(
        Props[QueueReportingActor]().withMailbox("pekko.actor.mailbox.unbounded-deque-based"),
        "default-override-from-props",
        UnboundedDeqMailboxTypes)
    }

    "get an bounded message queue when it's only configured with RequiresMailbox" in {
      checkMailboxQueue(Props[BoundedQueueReportingActor](), "default-override-from-trait", BoundedMailboxTypes)
    }

    "get an unbounded deque message queue when it's only mixed with Stash" in {
      checkMailboxQueue(Props[StashQueueReportingActor](), "default-override-from-stash", UnboundedDeqMailboxTypes)
      checkMailboxQueue(Props(new StashQueueReportingActor), "default-override-from-stash2", UnboundedDeqMailboxTypes)
      checkMailboxQueue(
        Props(classOf[StashQueueReportingActorWithParams], 17, "hello"),
        "default-override-from-stash3",
        UnboundedDeqMailboxTypes)
      checkMailboxQueue(
        Props(new StashQueueReportingActorWithParams(17, "hello")),
        "default-override-from-stash4",
        UnboundedDeqMailboxTypes)
    }

    "get a bounded message queue when it's configured as mailbox" in {
      checkMailboxQueue(Props[QueueReportingActor](), "default-bounded", BoundedMailboxTypes)
    }

    "get an unbounded deque message queue when it's configured as mailbox" in {
      checkMailboxQueue(Props[QueueReportingActor](), "default-unbounded-deque", UnboundedDeqMailboxTypes)
    }

    "get a bounded control aware message queue when it's configured as mailbox" in {
      checkMailboxQueue(Props[QueueReportingActor](), "default-bounded-control-aware", BoundedControlAwareMailboxTypes)
    }

    "get an unbounded control aware message queue when it's configured as mailbox" in {
      checkMailboxQueue(
        Props[QueueReportingActor](),
        "default-unbounded-control-aware",
        UnboundedControlAwareMailboxTypes)
    }

    "get an bounded control aware message queue when it's only configured with RequiresMailbox" in {
      checkMailboxQueue(
        Props[BoundedControlAwareQueueReportingActor](),
        "default-override-from-trait-bounded-control-aware",
        BoundedControlAwareMailboxTypes)
    }

    "get an unbounded control aware message queue when it's only configured with RequiresMailbox" in {
      checkMailboxQueue(
        Props[UnboundedControlAwareQueueReportingActor](),
        "default-override-from-trait-unbounded-control-aware",
        UnboundedControlAwareMailboxTypes)
    }

    "fail to create actor when an unbounded dequeu message queue is configured as mailbox overriding RequestMailbox" in {
      intercept[ConfigurationException](
        system.actorOf(Props[BoundedQueueReportingActor](), "default-unbounded-deque-override-trait"))
    }

    "get an unbounded message queue when defined in dispatcher" in {
      checkMailboxQueue(Props[QueueReportingActor](), "unbounded-default", UnboundedMailboxTypes)
    }

    "fail to create actor when an unbounded message queue is defined in dispatcher overriding RequestMailbox" in {
      intercept[ConfigurationException](
        system.actorOf(Props[BoundedQueueReportingActor](), "unbounded-default-override-trait"))
    }

    "get a bounded message queue when it's configured as mailbox overriding unbounded in dispatcher" in {
      checkMailboxQueue(Props[QueueReportingActor](), "unbounded-bounded", BoundedMailboxTypes)
    }

    "get a bounded message queue when defined in dispatcher" in {
      checkMailboxQueue(Props[QueueReportingActor](), "bounded-default", BoundedMailboxTypes)
    }

    "get a bounded message queue with 0 push timeout when defined in dispatcher" in {
      val q = checkMailboxQueue(
        Props[QueueReportingActor](),
        "default-bounded-mailbox-with-zero-pushtimeout",
        BoundedMailboxTypes)
      q.asInstanceOf[BoundedMessageQueueSemantics].pushTimeOut should ===(Duration.Zero)
    }

    "get an unbounded message queue when it's configured as mailbox overriding bounded in dispatcher" in {
      checkMailboxQueue(Props[QueueReportingActor](), "bounded-unbounded", UnboundedMailboxTypes)
    }

    "get an unbounded message queue overriding configuration on the props" in {
      checkMailboxQueue(
        Props[QueueReportingActor]().withMailbox("pekko.actor.mailbox.unbounded-deque-based"),
        "bounded-unbounded-override-props",
        UnboundedMailboxTypes)
    }

    "get a bounded deque-based message queue if configured and required" in {
      checkMailboxQueue(
        Props[StashQueueReportingActor](),
        "bounded-deque-requirements-configured",
        BoundedDeqMailboxTypes)
    }

    "fail with a unbounded deque-based message queue if configured and required" in {
      intercept[ConfigurationException](
        system.actorOf(Props[StashQueueReportingActor](), "bounded-deque-require-unbounded-configured"))
    }

    "fail with a bounded deque-based message queue if not configured" in {
      intercept[ConfigurationException](
        system.actorOf(Props[StashQueueReportingActor](), "bounded-deque-require-unbounded-unconfigured"))
    }

    "get a bounded deque-based message queue if configured and required with Props" in {
      checkMailboxQueue(
        Props[StashQueueReportingActor]()
          .withDispatcher("requiring-bounded-dispatcher")
          .withMailbox("pekko.actor.mailbox.bounded-deque-based"),
        "bounded-deque-requirements-configured-props",
        BoundedDeqMailboxTypes)
    }

    "fail with a unbounded deque-based message queue if configured and required with Props" in {
      intercept[ConfigurationException](
        system.actorOf(
          Props[StashQueueReportingActor]()
            .withDispatcher("requiring-bounded-dispatcher")
            .withMailbox("pekko.actor.mailbox.unbounded-deque-based"),
          "bounded-deque-require-unbounded-configured-props"))
    }

    "fail with a bounded deque-based message queue if not configured with Props" in {
      intercept[ConfigurationException](
        system.actorOf(
          Props[StashQueueReportingActor]().withDispatcher("requiring-bounded-dispatcher"),
          "bounded-deque-require-unbounded-unconfigured-props"))
    }

    "get a bounded deque-based message queue if configured and required with Props (dispatcher)" in {
      checkMailboxQueue(
        Props[StashQueueReportingActor]().withDispatcher("requiring-bounded-dispatcher"),
        "bounded-deque-requirements-configured-props-disp",
        BoundedDeqMailboxTypes)
    }

    "fail with a unbounded deque-based message queue if configured and required with Props (dispatcher)" in {
      intercept[ConfigurationException](
        system.actorOf(
          Props[StashQueueReportingActor]().withDispatcher("requiring-bounded-dispatcher"),
          "bounded-deque-require-unbounded-configured-props-disp"))
    }

    "fail with a bounded deque-based message queue if not configured with Props (dispatcher)" in {
      intercept[ConfigurationException](
        system.actorOf(
          Props[StashQueueReportingActor]().withDispatcher("requiring-bounded-dispatcher"),
          "bounded-deque-require-unbounded-unconfigured-props-disp"))
    }

    "get a bounded deque-based message queue if configured and required with Props (mailbox)" in {
      checkMailboxQueue(
        Props[StashQueueReportingActor]().withMailbox("pekko.actor.mailbox.bounded-deque-based"),
        "bounded-deque-requirements-configured-props-mail",
        BoundedDeqMailboxTypes)
    }

    "fail with a unbounded deque-based message queue if configured and required with Props (mailbox)" in {
      intercept[ConfigurationException](
        system.actorOf(
          Props[StashQueueReportingActor]().withMailbox("pekko.actor.mailbox.unbounded-deque-based"),
          "bounded-deque-require-unbounded-configured-props-mail"))
    }

    "fail with a bounded deque-based message queue if not configured with Props (mailbox)" in {
      intercept[ConfigurationException](
        system.actorOf(Props[StashQueueReportingActor](), "bounded-deque-require-unbounded-unconfigured-props-mail"))
    }

    "get an unbounded message queue with a balancing dispatcher" in {
      checkMailboxQueue(
        Props[QueueReportingActor]().withDispatcher("balancing-dispatcher"),
        "unbounded-balancing",
        UnboundedMailboxTypes)
    }

    "get a bounded message queue with a balancing bounded dispatcher" in {
      checkMailboxQueue(
        Props[QueueReportingActor]().withDispatcher("balancing-bounded-dispatcher"),
        "bounded-balancing",
        BoundedMailboxTypes)
    }

    "get a bounded message queue with a requiring balancing bounded dispatcher" in {
      checkMailboxQueue(
        Props[QueueReportingActor]().withDispatcher("requiring-balancing-bounded-dispatcher"),
        "requiring-bounded-balancing",
        BoundedMailboxTypes)
    }
  }
}
