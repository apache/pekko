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

package org.apache.pekko.persistence.query

import scala.reflect.ClassTag

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.persistence.{ PersistencePlugin, PluginProvider }
import pekko.persistence.query.scaladsl.ReadJournal
import pekko.util.unused

/**
 * Persistence extension for queries.
 */
object PersistenceQuery extends ExtensionId[PersistenceQuery] with ExtensionIdProvider {

  override def get(system: ActorSystem): PersistenceQuery = super.get(system)
  override def get(system: ClassicActorSystemProvider): PersistenceQuery = super.get(system)

  def createExtension(system: ExtendedActorSystem): PersistenceQuery = new PersistenceQuery(system)

  def lookup: PersistenceQuery.type = PersistenceQuery

  @InternalApi
  private[pekko] val pluginProvider: PluginProvider[ReadJournalProvider, ReadJournal, javadsl.ReadJournal] =
    new PluginProvider[ReadJournalProvider, scaladsl.ReadJournal, javadsl.ReadJournal] {
      override def scalaDsl(t: ReadJournalProvider): ReadJournal = t.scaladslReadJournal()
      override def javaDsl(t: ReadJournalProvider): javadsl.ReadJournal = t.javadslReadJournal()
    }

}

class PersistenceQuery(system: ExtendedActorSystem)
    extends PersistencePlugin[scaladsl.ReadJournal, javadsl.ReadJournal, ReadJournalProvider](system)(
      ClassTag(classOf[ReadJournalProvider]),
      PersistenceQuery.pluginProvider)
    with Extension {

  /**
   * Scala API: Returns the [[pekko.persistence.query.scaladsl.ReadJournal]] specified by the given
   * read journal configuration entry.
   *
   * The provided readJournalPluginConfig will be used to configure the journal plugin instead of the actor system
   * config.
   */
  final def readJournalFor[T <: scaladsl.ReadJournal](readJournalPluginId: String, readJournalPluginConfig: Config): T =
    pluginFor(readJournalPluginId, readJournalPluginConfig).scaladslPlugin.asInstanceOf[T]

  /**
   * Scala API: Returns the [[pekko.persistence.query.scaladsl.ReadJournal]] specified by the given
   * read journal configuration entry.
   */
  final def readJournalFor[T <: scaladsl.ReadJournal](readJournalPluginId: String): T =
    readJournalFor(readJournalPluginId, ConfigFactory.empty)

  /**
   * Java API: Returns the [[pekko.persistence.query.javadsl.ReadJournal]] specified by the given
   * read journal configuration entry.
   */
  final def getReadJournalFor[T <: javadsl.ReadJournal](
      @unused clazz: Class[T],
      readJournalPluginId: String,
      readJournalPluginConfig: Config): T =
    pluginFor(readJournalPluginId, readJournalPluginConfig).javadslPlugin.asInstanceOf[T]

  final def getReadJournalFor[T <: javadsl.ReadJournal](clazz: Class[T], readJournalPluginId: String): T =
    getReadJournalFor[T](clazz, readJournalPluginId, ConfigFactory.empty())

}
