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
  def apply[T](behavior: () => Behavior[T], props: Props, rethrowTypedFailure: Boolean): pekko.actor.Props = {
    val classicProps = pekko.actor.Props(new ActorAdapter(behavior(), rethrowTypedFailure))

    val dispatcherProps = (props.firstOrElse[DispatcherSelector](DispatcherDefault.empty) match {
      case _: DispatcherDefault          => classicProps
      case DispatcherFromConfig(name, _) => classicProps.withDispatcher(name)
      case _: DispatcherSameAsParent     => classicProps.withDispatcher(Deploy.DispatcherSameAsParent)
      case unknown                       => throw new RuntimeException(s"Unsupported dispatcher selector: $unknown")
    }).withDeploy(Deploy.local) // disallow remote deployment for typed actors

    val mailboxProps = props.firstOrElse[MailboxSelector](MailboxSelector.default()) match {
      case _: DefaultMailboxSelector           => dispatcherProps
      case BoundedMailboxSelector(capacity, _) =>
        // specific support in classic Mailboxes
        dispatcherProps.withMailbox(s"${Mailboxes.BoundedCapacityPrefix}$capacity")
      case MailboxFromConfigSelector(path, _) =>
        dispatcherProps.withMailbox(path)
      case unknown => throw new RuntimeException(s"Unsupported mailbox selector: $unknown")
    }

    val localDeploy = mailboxProps.withDeploy(Deploy.local) // disallow remote deployment for typed actors

    val tags = props.firstOrElse[ActorTags](ActorTagsImpl.empty).tags
    if (tags.isEmpty) localDeploy
    else localDeploy.withActorTags(tags)
  }

}
