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

package org.apache.pekko.persistence

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Consumer

import scala.annotation.tailrec
import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.control.NonFatal

import com.typesafe.config.{ Config, ConfigFactory }

import org.apache.pekko
import pekko.actor._
import pekko.annotation.InternalApi
import pekko.annotation.InternalStableApi
import pekko.event.{ Logging, LoggingAdapter }
import pekko.japi.Pair
import pekko.persistence.journal.{ EventAdapters, IdentityEventAdapters }
import pekko.util.Collections.EmptyImmutableSeq
import pekko.util.Helpers.ConfigOps
import pekko.util.Reflect

/**
 * Persistence configuration.
 */
final class PersistenceSettings(config: Config) {

  object atLeastOnceDelivery {

    val redeliverInterval: FiniteDuration =
      config.getMillisDuration("at-least-once-delivery.redeliver-interval")

    val redeliveryBurstLimit: Int =
      config.getInt("at-least-once-delivery.redelivery-burst-limit")

    val warnAfterNumberOfUnconfirmedAttempts: Int =
      config.getInt("at-least-once-delivery.warn-after-number-of-unconfirmed-attempts")

    val maxUnconfirmedMessages: Int =
      config.getInt("at-least-once-delivery.max-unconfirmed-messages")
  }

  /**
   * INTERNAL API.
   *
   * These config options are only used internally for testing
   * purposes and are therefore not defined in reference.conf
   */
  private[persistence] object internal {
    val publishPluginCommands: Boolean = {
      val path = "publish-plugin-commands"
      config.hasPath(path) && config.getBoolean(path)
    }

  }

}

/**
 * Identification of [[PersistentActor]].
 */
//#persistence-identity
trait PersistenceIdentity {

  /**
   * Id of the persistent entity for which messages should be replayed.
   */
  def persistenceId: String

  /**
   * Configuration id of the journal plugin servicing this persistent actor.
   * When empty, looks in `pekko.persistence.journal.plugin` to find configuration entry path.
   * When configured, uses `journalPluginId` as absolute path to the journal configuration entry.
   * Configuration entry must contain few required fields, such as `class`. See `src/main/resources/reference.conf`.
   */
  def journalPluginId: String = ""

  /**
   * Configuration id of the snapshot plugin servicing this persistent actor.
   * When empty, looks in `pekko.persistence.snapshot-store.plugin` to find configuration entry path.
   * When configured, uses `snapshotPluginId` as absolute path to the snapshot store configuration entry.
   * Configuration entry must contain few required fields, such as `class`. See `src/main/resources/reference.conf`.
   */
  def snapshotPluginId: String = ""
}

//#persistence-identity

trait PersistenceRecovery {
  // #persistence-recovery
  /**
   * Called when the persistent actor is started for the first time.
   * The returned [[pekko.persistence.Recovery]] object defines how the Actor will recover its persistent state before
   * handling the first incoming message.
   *
   * To skip recovery completely return `Recovery.none`.
   */
  def recovery: Recovery = Recovery()

  // #persistence-recovery
}

trait PersistenceStash extends Stash with StashFactory {

  /**
   * The returned [[pekko.persistence.StashOverflowStrategy]] object determines how to handle the message failed to stash
   * when the internal Stash capacity exceeded.
   */
  def internalStashOverflowStrategy: StashOverflowStrategy =
    Persistence(context.system).defaultInternalStashOverflowStrategy
}

trait RuntimePluginConfig {

  /**
   * Additional configuration of the journal plugin servicing this persistent actor.
   * When empty, the whole configuration of the journal plugin will be taken from the [[com.typesafe.config.Config]] loaded into the
   * [[pekko.actor.ActorSystem]].
   * When configured, the journal plugin configuration will be taken from this [[com.typesafe.config.Config]] merged with the [[com.typesafe.config.Config]]
   * loaded into the [[pekko.actor.ActorSystem]].
   *
   * @return an additional configuration used to configure the journal plugin.
   */
  def journalPluginConfig: Config

  /**
   * Additional configuration of the snapshot plugin servicing this persistent actor.
   * When empty, the whole configuration of the snapshot plugin will be taken from the [[com.typesafe.config.Config]] loaded into the
   * [[pekko.actor.ActorSystem]].
   * When configured, the snapshot plugin configuration will be taken from this [[com.typesafe.config.Config]] merged with the [[com.typesafe.config.Config]]
   * loaded into the [[pekko.actor.ActorSystem]].
   *
   * @return an additional configuration used to configure the snapshot plugin.
   */
  def snapshotPluginConfig: Config
}

/**
 * Persistence extension provider.
 */
object Persistence extends ExtensionId[Persistence] with ExtensionIdProvider {

  /** Java API. */
  override def get(system: ActorSystem): Persistence = super.get(system)

  override def get(system: ClassicActorSystemProvider): Persistence = super.get(system)

  def createExtension(system: ExtendedActorSystem): Persistence = new Persistence(system)

  def lookup = Persistence

  /** INTERNAL API. */
  private[persistence] case class PluginHolder(actorFactory: () => ActorRef, adapters: EventAdapters, config: Config)
      extends Extension {
    // lazy creation of actor so that it's not started when only looking up adapters
    lazy val actor: ActorRef = actorFactory()
  }

  /** Config path to fall-back to if a setting is not defined in a specific plugin's config section */
  val JournalFallbackConfigPath = "pekko.persistence.journal-plugin-fallback"

  /** Config path to fall-back to if a setting is not defined in a specific snapshot plugin's config section */
  val SnapshotStoreFallbackConfigPath = "pekko.persistence.snapshot-store-plugin-fallback"

  /**
   * INTERNAL API
   * @throws java.lang.IllegalArgumentException if config path for the `pluginId` doesn't exist
   */
  @InternalApi private[pekko] def verifyPluginConfigExists(
      config: Config,
      pluginId: String,
      pluginType: String): Unit = {
    if (!isEmpty(pluginId) && !config.hasPath(pluginId))
      throw new IllegalArgumentException(s"$pluginType plugin [$pluginId] configuration doesn't exist.")
  }

  /**
   * INTERNAL API
   * @throws java.lang.IllegalArgumentException if `pluginId` is empty (undefined)
   */
  @InternalApi private[pekko] def verifyPluginConfigIsDefined(pluginId: String, pluginType: String): Unit = {
    if (isEmpty(pluginId))
      throw new IllegalArgumentException(s"$pluginType plugin is not configured, see 'reference.conf'")
  }

  /** Check for default or missing identity. */
  private def isEmpty(text: String) = {
    (text eq null) || text.isEmpty
  }
}

/**
 * Persistence extension.
 */
class Persistence(val system: ExtendedActorSystem) extends Extension {

  import Persistence._

  private def log: LoggingAdapter = Logging(system, classOf[Persistence])

  private val NoSnapshotStorePluginId = "pekko.persistence.no-snapshot-store"

  private val config = system.settings.config.getConfig("pekko.persistence")

  /**
   * INTERNAL API: When starting many persistent actors at the same time the journal
   * its data store is protected from being overloaded by limiting number
   * of recoveries that can be in progress at the same time.
   */
  @InternalApi private[pekko] val recoveryPermitter: ActorRef = {
    val maxPermits = config.getInt("max-concurrent-recoveries")
    system.systemActorOf(RecoveryPermitter.props(maxPermits), "recoveryPermitter")
  }

  // Lazy, so user is not forced to configure defaults when she is not using them.
  private lazy val defaultJournalPluginId = {
    val configPath = config.getString("journal.plugin")
    verifyPluginConfigIsDefined(configPath, "Default journal")
    verifyJournalPluginConfigExists(ConfigFactory.empty, configPath)
    configPath
  }

  // Lazy, so user is not forced to configure defaults when she is not using them.
  private lazy val defaultSnapshotPluginId = {
    val configPath = config.getString("snapshot-store.plugin")

    if (isEmpty(configPath)) {
      log.warning(
        "No default snapshot store configured! " +
        "To configure a default snapshot-store plugin set the `pekko.persistence.snapshot-store.plugin` key. " +
        "For details see 'reference.conf'")
      NoSnapshotStorePluginId
    } else {
      verifySnapshotPluginConfigExists(ConfigFactory.empty, configPath)
      configPath
    }
  }

  // Lazy, so user is not forced to configure defaults when she is not using them.
  lazy val defaultInternalStashOverflowStrategy: StashOverflowStrategy =
    system.dynamicAccess
      .createInstanceFor[StashOverflowStrategyConfigurator](
        config.getString("internal-stash-overflow-strategy"),
        EmptyImmutableSeq)
      .map(_.create(system.settings.config))
      .get

  val settings = new PersistenceSettings(config)

  /** Discovered persistence journal and snapshot store plugins. */
  private val pluginExtensionId = new AtomicReference[Map[String, ExtensionId[PluginHolder]]](Map.empty)

  config
    .getStringList("journal.auto-start-journals")
    .forEach(new Consumer[String] {
      override def accept(id: String): Unit = {
        log.info(s"Auto-starting journal plugin `$id`")
        journalFor(id)
      }
    })
  config
    .getStringList("snapshot-store.auto-start-snapshot-stores")
    .forEach(new Consumer[String] {
      override def accept(id: String): Unit = {
        log.info(s"Auto-starting snapshot store `$id`")
        snapshotStoreFor(id)
      }
    })

  /**
   * @throws java.lang.IllegalArgumentException if `configPath` doesn't exist
   */
  private def verifyJournalPluginConfigExists(pluginConfig: Config, configPath: String): Unit =
    verifyPluginConfigExists(pluginConfig.withFallback(system.settings.config), configPath, "Journal")

  /**
   * @throws java.lang.IllegalArgumentException if `configPath` doesn't exist
   */
  private def verifySnapshotPluginConfigExists(pluginConfig: Config, configPath: String): Unit =
    verifyPluginConfigExists(pluginConfig.withFallback(system.settings.config), configPath, "Snapshot store")

  /**
   * Returns an [[pekko.persistence.journal.EventAdapters]] object which serves as a per-journal collection of bound event adapters.
   * If no adapters are registered for a given journal the EventAdapters object will simply return the identity
   * adapter for each class, otherwise the most specific adapter matching a given class will be returned.
   */
  final def adaptersFor(journalPluginId: String): EventAdapters = {
    adaptersFor(journalPluginId: String, ConfigFactory.empty)
  }

  /**
   * Returns an [[pekko.persistence.journal.EventAdapters]] object which serves as a per-journal collection of bound event adapters.
   * If no adapters are registered for a given journal the EventAdapters object will simply return the identity
   * adapter for each class, otherwise the most specific adapter matching a given class will be returned.
   *
   * The provided journalPluginConfig will be used to configure the plugin instead of the actor system config.
   */
  final def adaptersFor(journalPluginId: String, journalPluginConfig: Config): EventAdapters = {
    val configPath = if (isEmpty(journalPluginId)) defaultJournalPluginId else journalPluginId
    verifyJournalPluginConfigExists(journalPluginConfig, configPath)
    pluginHolderFor(configPath, JournalFallbackConfigPath, journalPluginConfig).adapters
  }

  /**
   * INTERNAL API
   * Looks up [[pekko.persistence.journal.EventAdapters]] by journal plugin's ActorRef.
   */
  private[pekko] final def adaptersFor(journalPluginActor: ActorRef): EventAdapters = {
    pluginExtensionId.get().values.collectFirst {
      case ext if ext(system).actor == journalPluginActor => ext(system).adapters
    } match {
      case Some(adapters) => adapters
      case _              => IdentityEventAdapters
    }
  }

  /**
   * INTERNAL API
   * Returns the plugin config identified by `pluginId`.
   * When empty, looks in `pekko.persistence.journal.plugin` to find configuration entry path.
   * When configured, uses `journalPluginId` as absolute path to the journal configuration entry.
   */
  private[pekko] final def journalConfigFor(
      journalPluginId: String,
      journalPluginConfig: Config = ConfigFactory.empty): Config = {
    val configPath = if (isEmpty(journalPluginId)) defaultJournalPluginId else journalPluginId
    verifyJournalPluginConfigExists(journalPluginConfig, configPath)
    pluginHolderFor(configPath, JournalFallbackConfigPath, journalPluginConfig).config
  }

  /**
   * INTERNAL API
   * Looks up the plugin config by plugin's ActorRef.
   */
  private[pekko] final def configFor(journalPluginActor: ActorRef): Config =
    pluginExtensionId.get().values.collectFirst {
      case ext if ext(system).actor == journalPluginActor => ext(system).config
    } match {
      case Some(conf) => conf
      case None       => throw new IllegalArgumentException(s"Unknown plugin actor $journalPluginActor")
    }

  /**
   * INTERNAL API
   * Returns a journal plugin actor identified by `journalPluginId`.
   * When empty, looks in `pekko.persistence.journal.plugin` to find configuration entry path.
   * When configured, uses `journalPluginId` as absolute path to the journal configuration entry.
   * Configuration entry must contain few required fields, such as `class`. See `src/main/resources/reference.conf`.
   */
  @InternalStableApi
  private[pekko] final def journalFor(
      journalPluginId: String,
      journalPluginConfig: Config = ConfigFactory.empty): ActorRef = {
    val configPath = if (isEmpty(journalPluginId)) defaultJournalPluginId else journalPluginId
    verifyJournalPluginConfigExists(journalPluginConfig, configPath)
    pluginHolderFor(configPath, JournalFallbackConfigPath, journalPluginConfig).actor
  }

  /**
   * INTERNAL API
   *
   * Returns a snapshot store plugin actor identified by `snapshotPluginId`.
   * When empty, looks in `pekko.persistence.snapshot-store.plugin` to find configuration entry path.
   * When configured, uses `snapshotPluginId` as absolute path to the snapshot store configuration entry.
   * Configuration entry must contain few required fields, such as `class`. See `src/main/resources/reference.conf`.
   */
  @InternalStableApi
  private[pekko] final def snapshotStoreFor(
      snapshotPluginId: String,
      snapshotPluginConfig: Config = ConfigFactory.empty): ActorRef = {
    val configPath = if (isEmpty(snapshotPluginId)) defaultSnapshotPluginId else snapshotPluginId
    verifySnapshotPluginConfigExists(snapshotPluginConfig, configPath)
    pluginHolderFor(configPath, SnapshotStoreFallbackConfigPath, snapshotPluginConfig).actor
  }

  @tailrec private def pluginHolderFor(
      configPath: String,
      fallbackPath: String,
      additionalConfig: Config): PluginHolder = {
    val extensionIdMap = pluginExtensionId.get
    extensionIdMap.get(configPath) match {
      case Some(extensionId) =>
        extensionId(system)
      case None =>
        val extensionId = new PluginHolderExtensionId(configPath, fallbackPath, additionalConfig)
        pluginExtensionId.compareAndSet(extensionIdMap, extensionIdMap.updated(configPath, extensionId))
        pluginHolderFor(configPath, fallbackPath, additionalConfig) // Recursive invocation.
    }
  }

  private def createPlugin(configPath: String, pluginConfig: Config): ActorRef = {
    val pluginActorName = configPath
    val pluginClassName = pluginConfig.getString("class")
    if (isEmpty(pluginClassName))
      throw new IllegalArgumentException(s"Plugin class name must be defined in config property [$configPath.class]")
    log.debug(s"Create plugin: $pluginActorName $pluginClassName")
    val pluginClass = system.dynamicAccess.getClassFor[Any](pluginClassName).get
    val pluginDispatcherId = pluginConfig.getString("plugin-dispatcher")
    val pluginActorArgs: List[AnyRef] =
      try {
        Reflect.findConstructor(pluginClass, List(pluginConfig, configPath)) // will throw if not found
        List(pluginConfig, configPath)
      } catch {
        case NonFatal(_) =>
          try {
            Reflect.findConstructor(pluginClass, List(pluginConfig)) // will throw if not found
            List(pluginConfig)
          } catch {
            case NonFatal(_) => Nil
          } // otherwise use empty constructor
      }
    val pluginActorProps = Props(Deploy(dispatcher = pluginDispatcherId), pluginClass, pluginActorArgs)
    system.systemActorOf(pluginActorProps, pluginActorName)
  }

  private def createAdapters(configPath: String, additionalConfig: Config): EventAdapters = {
    val pluginConfig = additionalConfig.withFallback(system.settings.config).getConfig(configPath)
    EventAdapters(system, pluginConfig)
  }

  /** Creates a canonical persistent actor id from a persistent actor ref. */
  def persistenceId(persistentActor: ActorRef): String = id(persistentActor)

  private def id(ref: ActorRef) = ref.path.toStringWithoutAddress

  private class PluginHolderExtensionId(configPath: String, fallbackPath: String, additionalConfig: Config)
      extends ExtensionId[PluginHolder] {
    def this(configPath: String, fallbackPath: String) = this(configPath, fallbackPath, ConfigFactory.empty)

    override def createExtension(system: ExtendedActorSystem): PluginHolder = {
      val mergedConfig = additionalConfig.withFallback(system.settings.config)
      require(
        !isEmpty(configPath) && mergedConfig.hasPath(configPath),
        s"'reference.conf' is missing persistence plugin config path: '$configPath'")
      val config: Config = mergedConfig.getConfig(configPath).withFallback(mergedConfig.getConfig(fallbackPath))
      val pluginActorFactory = () => createPlugin(configPath, config)
      val adapters: EventAdapters = createAdapters(configPath, mergedConfig)

      PluginHolder(pluginActorFactory, adapters, config)
    }
  }

  /**
   * A slice is deterministically defined based on the persistence id.
   * `numberOfSlices` is not configurable because changing the value would result in
   * different slice for a persistence id than what was used before, which would
   * result in invalid eventsBySlices.
   *
   * `numberOfSlices` is 1024
   */
  final def numberOfSlices: Int = 1024

  /**
   * A slice is deterministically defined based on the persistence id. The purpose is to
   * evenly distribute all persistence ids over the slices and be able to query the
   * events for a range of slices.
   */
  final def sliceForPersistenceId(persistenceId: String): Int =
    math.abs(persistenceId.hashCode % numberOfSlices)

  /**
   * Scala API: Split the total number of slices into ranges by the given `numberOfRanges`.
   *
   * For example, `numberOfSlices` is 1024 and given 4 `numberOfRanges` this method will
   * return ranges (0 to 255), (256 to 511), (512 to 767) and (768 to 1023).
   */
  final def sliceRanges(numberOfRanges: Int): immutable.IndexedSeq[Range] = {
    val rangeSize = numberOfSlices / numberOfRanges
    require(
      numberOfRanges * rangeSize == numberOfSlices,
      s"numberOfRanges [$numberOfRanges] must be a whole number divisor of numberOfSlices [$numberOfSlices].")
    (0 until numberOfRanges).map { i =>
      i * rangeSize until i * rangeSize + rangeSize
    }.toVector
  }

  /**
   * Java API: Split the total number of slices into ranges by the given `numberOfRanges`.
   *
   * For example, `numberOfSlices` is 128 and given 4 `numberOfRanges` this method will
   * return ranges (0 to 255), (256 to 511), (512 to 767) and (768 to 1023).
   */
  final def getSliceRanges(numberOfRanges: Int): java.util.List[Pair[Integer, Integer]] = {
    import org.apache.pekko.util.ccompat.JavaConverters._
    sliceRanges(numberOfRanges).map(range => Pair(Integer.valueOf(range.min), Integer.valueOf(range.max))).asJava
  }

}
