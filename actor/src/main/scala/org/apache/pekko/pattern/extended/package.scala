/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko
package pattern

/**
 * == Extended Versions Of Pekko Patterns ==
 *
 * This subpackage contains extended versions of Akka patterns.
 *
 * Currently supported are:
 *
 * <ul>
 * <li><b>ask:</b> create a temporary one-off actor for receiving a reply to a
 * message and complete a [[scala.concurrent.Future]] with it; returns said
 * Future.</li>
 * a message.</li>
 * </ul>
 *
 * In Scala the recommended usage is to import the pattern from the package
 * object:
 * {{{
 * import org.apache.pekko.pattern.extended.ask
 *
 * ask(actor, askSender => Request(askSender)) // use it directly
 * actor ask (Request(_))   // use it by implicit conversion
 * }}}
 *
 * For Java the patterns are available as static methods of the [[org.apache.pekko.pattern.Patterns]]
 * class:
 * {{{
 * import static org.apache.pekko.pattern.Patterns.ask;
 *
 * ask(actor, new org.apache.pekko.japi.Function<ActorRef, Object> {
 *   Object apply(ActorRef askSender) {
 *     return new Request(askSender);
 *   }
 * });
 * }}}
 */
package object extended extends ExplicitAskSupport
