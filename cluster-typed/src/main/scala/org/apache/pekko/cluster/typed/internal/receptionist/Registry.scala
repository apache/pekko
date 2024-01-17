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

package org.apache.pekko.cluster.typed.internal.receptionist

import org.apache.pekko
import pekko.actor.typed.ActorRef
import pekko.actor.typed.internal.receptionist.AbstractServiceKey
import pekko.actor.typed.receptionist.ServiceKey
import pekko.annotation.InternalApi
import pekko.cluster.UniqueAddress
import pekko.cluster.ddata.{ ORMultiMap, ORMultiMapKey, SelfUniqueAddress }
import pekko.cluster.typed.internal.receptionist.ClusterReceptionist.{ DDataKey, EmptyORMultiMap, Entry }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ShardedServiceRegistry {
  def apply(numberOfKeys: Int): ShardedServiceRegistry = {
    val emptyRegistries = (0 until numberOfKeys).map { n =>
      val key = ORMultiMapKey[ServiceKey[?], Entry](s"ReceptionistKey_$n")
      key -> new ServiceRegistry(EmptyORMultiMap)
    }.toMap
    new ShardedServiceRegistry(emptyRegistries, Set.empty, Set.empty)
  }

}

/**
 * INTERNAL API
 *
 * Two level structure for keeping service registry to be able to shard entries over multiple ddata keys (to not
 * get too large ddata messages)
 */
@InternalApi private[pekko] final case class ShardedServiceRegistry(
    serviceRegistries: Map[DDataKey, ServiceRegistry],
    nodes: Set[UniqueAddress],
    unreachable: Set[UniqueAddress]) {

  private val keys = serviceRegistries.keySet.toArray

  def registryFor(ddataKey: DDataKey): ServiceRegistry = serviceRegistries(ddataKey)

  def allDdataKeys: Iterable[DDataKey] = keys

  def ddataKeyFor(serviceKey: ServiceKey[?]): DDataKey =
    keys(math.abs(serviceKey.id.hashCode() % serviceRegistries.size))

  def allServices: Iterator[(ServiceKey[?], Set[Entry])] =
    serviceRegistries.valuesIterator.flatMap(_.entries.entries)

  def allEntries: Iterator[Entry] = allServices.flatMap(_._2)

  def actorRefsFor[T](key: ServiceKey[T]): Set[ActorRef[T]] = {
    val ddataKey = ddataKeyFor(key)
    serviceRegistries(ddataKey).actorRefsFor(key)
  }

  /**
   * @return keys that has a registered service instance on the given `address`
   */
  def keysFor(address: UniqueAddress)(implicit node: SelfUniqueAddress): Set[AbstractServiceKey] =
    serviceRegistries.valuesIterator.flatMap(_.keysFor(address)).toSet

  def withServiceRegistry(ddataKey: DDataKey, registry: ServiceRegistry): ShardedServiceRegistry =
    copy(serviceRegistries + (ddataKey -> registry))

  def allUniqueAddressesInState(selfUniqueAddress: UniqueAddress): Set[UniqueAddress] =
    allEntries.collect {
      // we don't care about local (empty host:port addresses)
      case entry if entry.ref.path.address.hasGlobalScope =>
        entry.uniqueAddress(selfUniqueAddress.address)
    }.toSet

  def collectChangedKeys(ddataKey: DDataKey, newRegistry: ServiceRegistry): Set[AbstractServiceKey] = {
    val previousRegistry = registryFor(ddataKey)
    ServiceRegistry.collectChangedKeys(previousRegistry, newRegistry)
  }

  def entriesPerDdataKey(
      entries: Map[AbstractServiceKey, Set[Entry]]): Map[DDataKey, Map[AbstractServiceKey, Set[Entry]]] =
    entries.foldLeft(Map.empty[DDataKey, Map[AbstractServiceKey, Set[Entry]]]) {
      case (acc, (key, entries)) =>
        val ddataKey = ddataKeyFor(key.asServiceKey)
        val updated = acc.getOrElse(ddataKey, Map.empty) + (key -> entries)
        acc + (ddataKey -> updated)
    }

  def addNode(node: UniqueAddress): ShardedServiceRegistry =
    copy(nodes = nodes + node)

  def removeNode(node: UniqueAddress): ShardedServiceRegistry =
    copy(nodes = nodes - node, unreachable = unreachable - node)

  def addUnreachable(uniqueAddress: UniqueAddress): ShardedServiceRegistry =
    copy(unreachable = unreachable + uniqueAddress)

  def removeUnreachable(uniqueAddress: UniqueAddress): ShardedServiceRegistry =
    copy(unreachable = unreachable - uniqueAddress)

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] final case class ServiceRegistry(entries: ORMultiMap[ServiceKey[?], Entry]) extends AnyVal {

  // let's hide all the ugly casts we can in here
  def actorRefsFor[T](key: AbstractServiceKey): Set[ActorRef[key.Protocol]] =
    entriesFor(key).map(_.ref.asInstanceOf[ActorRef[key.Protocol]])

  def entriesFor(key: AbstractServiceKey): Set[Entry] =
    entries.getOrElse(key.asServiceKey, Set.empty[Entry])

  def keysFor(address: UniqueAddress)(implicit node: SelfUniqueAddress): Set[ServiceKey[?]] =
    entries.entries.collect {
      case (key, entries) if entries.exists(_.uniqueAddress(node.uniqueAddress.address) == address) =>
        key
    }.toSet

  def addBinding[T](key: ServiceKey[T], value: Entry)(implicit node: SelfUniqueAddress): ServiceRegistry =
    copy(entries = entries.addBinding(node, key, value))

  def removeBinding[T](key: ServiceKey[T], value: Entry)(implicit node: SelfUniqueAddress): ServiceRegistry =
    copy(entries = entries.removeBinding(node, key, value))

  def removeAll(entries: Map[AbstractServiceKey, Set[Entry]])(implicit node: SelfUniqueAddress): ServiceRegistry = {
    entries.foldLeft(this) {
      case (acc, (key, entries)) =>
        entries.foldLeft(acc) {
          case (innerAcc, entry) =>
            innerAcc.removeBinding[key.Protocol](key.asServiceKey, entry)
        }
    }
  }

  def toORMultiMap: ORMultiMap[ServiceKey[?], Entry] = entries

}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object ServiceRegistry {
  final val Empty = ServiceRegistry(EmptyORMultiMap)

  def collectChangedKeys(previousRegistry: ServiceRegistry, newRegistry: ServiceRegistry): Set[AbstractServiceKey] = {
    val allKeys = previousRegistry.toORMultiMap.entries.keySet ++ newRegistry.toORMultiMap.entries.keySet
    allKeys.foldLeft(Set.empty[AbstractServiceKey]) { (acc, key) =>
      val oldValues = previousRegistry.entriesFor(key)
      val newValues = newRegistry.entriesFor(key)
      if (oldValues != newValues) acc + key
      else acc
    }
  }
}
