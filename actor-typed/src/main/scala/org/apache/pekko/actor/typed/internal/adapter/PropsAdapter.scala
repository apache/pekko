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

package org.apache.pekko.actor.typed.internal.adapter

import org.apache.pekko
import pekko.actor.Deploy
import pekko.actor.LocalScope
import pekko.actor.TypedCreatorFunctionConsumer
import pekko.actor.typed.ActorTags
import pekko.actor.typed.Behavior
import pekko.actor.typed.DispatcherSelector
import pekko.actor.typed.MailboxSelector
import pekko.actor.typed.Props
import pekko.actor.typed.internal.PropsImpl._
import pekko.annotation.InternalApi
import pekko.dispatch.Mailboxes

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object PropsAdapter {
  private final val TypedCreatorFunctionConsumerClazz = classOf[TypedCreatorFunctionConsumer]
  private final val DefaultTypedDeploy = Deploy.local.copy(mailbox = "pekko.actor.typed.default-mailbox")

  def apply[T](behavior: () => Behavior[T], props: Props, rethrowTypedFailure: Boolean): pekko.actor.Props = {
    val deploy =
      if (props eq Props.empty) DefaultTypedDeploy // optimized case with no props specified
      else {
        val deployWithMailbox = props.firstOrElse[MailboxSelector](MailboxSelector.default()) match {
          case _: DefaultMailboxSelector           => DefaultTypedDeploy
          case BoundedMailboxSelector(capacity, _) =>
            // specific support in classic Mailboxes
            DefaultTypedDeploy.copy(mailbox = s"${Mailboxes.BoundedCapacityPrefix}$capacity")
          case MailboxFromConfigSelector(path, _) => DefaultTypedDeploy.copy(mailbox = path)
          case unknown                            => throw new RuntimeException(s"Unsupported mailbox selector: $unknown")
        }

        val deployWithDispatcher = props.firstOrElse[DispatcherSelector](DispatcherDefault.empty) match {
          case _: DispatcherDefault          => deployWithMailbox
          case DispatcherFromConfig(name, _) => deployWithMailbox.copy(dispatcher = name)
          case _: DispatcherSameAsParent     => deployWithMailbox.copy(dispatcher = Deploy.DispatcherSameAsParent)
          case unknown                       => throw new RuntimeException(s"Unsupported dispatcher selector: $unknown")
        }

        val tags = props.firstOrElse[ActorTags](ActorTagsImpl.empty).tags
        val deployWithTags =
          if (tags.isEmpty) deployWithDispatcher else deployWithDispatcher.withTags(tags)

        if (deployWithTags.scope != LocalScope) // only replace if changed, withDeploy is expensive
          deployWithTags.copy(scope = Deploy.local.scope) // disallow remote deployment for typed actors
        else deployWithTags
      }

    // avoid the apply methods and also avoid copying props, for performance reasons
    new pekko.actor.Props(
      deploy,
      TypedCreatorFunctionConsumerClazz,
      classOf[ActorAdapter[_]] :: (() => new ActorAdapter(behavior(), rethrowTypedFailure)) :: Nil)
  }

}
