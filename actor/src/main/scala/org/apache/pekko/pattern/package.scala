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

/**
 * == Commonly Used Patterns With Akka ==
 *
 * This package is used as a collection point for usage patterns which involve
 * actors, futures, etc. but are loosely enough coupled to (multiple of) them
 * to present them separately from the core implementation. Currently supported
 * are:
 *
 * <ul>
 * <li><b>ask:</b> create a temporary one-off actor for receiving a reply to a
 * message and complete a [[scala.concurrent.Future]] with it; returns said
 * Future.</li>
 * <li><b>pipeTo:</b> feed eventually computed value of a future to an [[org.apache.pekko.actor.ActorRef]] as
 * a message.</li>
 * <li><b>pipeToSelection:</b> feed eventually computed value of a future to an [[org.apache.pekko.actor.ActorSelection]] as
 * a message.</li>
 * </ul>
 *
 * In Scala the recommended usage is to import the pattern from the package
 * object:
 * {{{
 * import org.apache.pekko.pattern.ask
 *
 * ask(actor, message) // use it directly
 * actor ask message   // use it by implicit conversion
 * }}}
 *
 * For Java the patterns are available as static methods of the [[org.apache.pekko.pattern.Patterns]]
 * class:
 * {{{
 * import static org.apache.pekko.pattern.Patterns.ask;
 *
 * ask(actor, message);
 * }}}
 */
package object pattern
    extends PipeToSupport
    with AskSupport
    with GracefulStopSupport
    with FutureTimeoutSupport
    with RetrySupport
