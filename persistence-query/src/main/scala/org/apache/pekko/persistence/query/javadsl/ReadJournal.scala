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

package org.apache.pekko.persistence.query.javadsl

/**
 * API for reading persistent events and information derived
 * from stored persistent events.
 *
 * The purpose of the API is not to enforce compatibility between different
 * journal implementations, because the technical capabilities may be very different.
 * The interface is very open so that different journals may implement specific queries.
 *
 * There are a few pre-defined queries that a query implementation may implement,
 * such as [[EventsByPersistenceIdQuery]], [[PersistenceIdsQuery]] and [[EventsByTagQuery]]
 * Implementation of these queries are optional and query (journal) plugins may define
 * their own specialized queries by implementing other methods.
 *
 * Usage:
 * {{{
 * SomeCoolReadJournal journal =
 *   PersistenceQuery.get(system).getReadJournalFor(SomeCoolReadJournal.class, queryPluginConfigPath);
 * Source<EventEnvolope, Unit> events = journal.eventsByTag("mytag", 0L);
 * }}}
 *
 * For Scala API see [[org.apache.pekko.persistence.query.scaladsl.ReadJournal]].
 */
trait ReadJournal
