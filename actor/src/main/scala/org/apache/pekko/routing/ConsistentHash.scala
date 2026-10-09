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

package org.apache.pekko.routing

import java.util.Arrays

import scala.collection.immutable
import scala.reflect.ClassTag

/**
 * Consistent Hashing node ring implementation.
 *
 * A good explanation of Consistent Hashing:
 * https://tom-e-white.com/2007/11/consistent-hashing.html
 *
 * Note that toString of the ring nodes are used for the node
 * hash, i.e. make sure it is different for different nodes.
 *
 * If virtual nodes of different nodes hash to the same ring position, the node
 * with the lowest toString owns that position, so the ring does not depend on the
 * order in which nodes were added.
 */
class ConsistentHash[T: ClassTag] private (
    nodes: immutable.SortedMap[Int, T],
    // other nodes whose virtual nodes hash to an owned ring position, kept so that
    // they can take over the position if its owner is removed
    collisions: immutable.Map[Int, List[T]],
    val virtualNodesFactor: Int) {

  import ConsistentHash._

  if (virtualNodesFactor < 1) throw new IllegalArgumentException("virtualNodesFactor must be >= 1")

  // arrays for fast binary search and access
  // nodeHashRing is the sorted hash values of the nodes
  // nodeRing is the nodes sorted in the same order as nodeHashRing, i.e. same index
  private val (nodeHashRing: Array[Int], nodeRing: Array[T]) = {
    val (nhr: Seq[Int], nr: Seq[T]) = nodes.toSeq.unzip
    (nhr.toArray, nr.toArray)
  }

  /**
   * Adds a node to the node ring.
   * Note that the instance is immutable and this
   * operation returns a new instance.
   */
  def :+(node: T): ConsistentHash[T] = {
    val (newNodes, newCollisions) = claim(nodes, collisions, node, virtualNodesFactor)
    new ConsistentHash(newNodes, newCollisions, virtualNodesFactor)
  }

  /**
   * Java API: Adds a node to the node ring.
   * Note that the instance is immutable and this
   * operation returns a new instance.
   */
  def add(node: T): ConsistentHash[T] = this :+ node

  /**
   * Removes a node from the node ring.
   * Note that the instance is immutable and this
   * operation returns a new instance.
   */
  def :-(node: T): ConsistentHash[T] = {
    val nodeHash = hashFor(node.toString)
    val (newNodes, newCollisions) = (1 to virtualNodesFactor).foldLeft((nodes, collisions)) {
      case ((ns, cs), r) =>
        val hash = concatenateNodeHash(nodeHash, r)
        val others = claimants(ns, cs, hash).filterNot(sameNode(_, node))
        setClaimants(ns, cs, hash, others)
    }
    new ConsistentHash(newNodes, newCollisions, virtualNodesFactor)
  }

  /**
   * Java API: Removes a node from the node ring.
   * Note that the instance is immutable and this
   * operation returns a new instance.
   */
  def remove(node: T): ConsistentHash[T] = this :- node

  // converts the result of Arrays.binarySearch into a index in the nodeRing array
  // see documentation of Arrays.binarySearch for what it returns
  private def idx(i: Int): Int = {
    if (i >= 0) i // exact match
    else {
      val j = math.abs(i + 1)
      if (j >= nodeHashRing.length) 0 // after last, use first
      else j // next node clockwise
    }
  }

  /**
   * Get the node responsible for the data key.
   * Can only be used if nodes exists in the node ring,
   * otherwise throws `IllegalStateException`
   */
  def nodeFor(key: Array[Byte]): T = {
    if (isEmpty) throw new IllegalStateException("Can't get node for [%s] from an empty node ring".format(key))

    nodeRing(idx(Arrays.binarySearch(nodeHashRing, hashFor(key))))
  }

  /**
   * Get the node responsible for the data key.
   * Can only be used if nodes exists in the node ring,
   * otherwise throws `IllegalStateException`
   */
  def nodeFor(key: String): T = {
    if (isEmpty) throw new IllegalStateException("Can't get node for [%s] from an empty node ring".format(key))

    nodeRing(idx(Arrays.binarySearch(nodeHashRing, hashFor(key))))
  }

  /**
   * Is the node ring empty, i.e. no nodes added or all removed.
   */
  def isEmpty: Boolean = nodes.isEmpty

}

object ConsistentHash {
  def apply[T: ClassTag](nodes: Iterable[T], virtualNodesFactor: Int): ConsistentHash[T] = {
    if (virtualNodesFactor < 1) throw new IllegalArgumentException("virtualNodesFactor must be >= 1")
    val nodeArray = nodes.toArray
    val total = nodeArray.length * virtualNodesFactor
    // each virtual node is encoded as (ring position << 32 | index), so that a primitive sort orders
    // them by ring position, and by insertion order for the same position
    val points = new Array[Long](total)
    var n = 0
    while (n < nodeArray.length) {
      val nodeHash = hashFor(nodeArray(n).toString)
      var r = 1
      while (r <= virtualNodesFactor) {
        val i = n * virtualNodesFactor + r - 1
        points(i) = (concatenateNodeHash(nodeHash, r).toLong << 32) | i
        r += 1
      }
      n += 1
    }
    Arrays.sort(points)

    def hashAt(i: Int): Int = (points(i) >> 32).toInt
    def nodeAt(i: Int): T = nodeArray((points(i) & 0xFFFFFFFFL).toInt / virtualNodesFactor)

    val ring = immutable.TreeMap.newBuilder[Int, T]
    var collisions = immutable.Map.empty[Int, List[T]]
    var i = 0
    while (i < total) {
      val hash = hashAt(i)
      var end = i + 1
      while (end < total && hashAt(end) == hash) end += 1
      if (end == i + 1) ring += hash -> nodeAt(i)
      else {
        // rare: several virtual nodes at the same ring position, resolve them like `:+` does
        var claimants = List.empty[T]
        var j = i
        while (j < end) {
          val node = nodeAt(j)
          claimants = node :: claimants.filterNot(sameNode(_, node))
          j += 1
        }
        val (owner, others) = ownerOf(claimants)
        ring += hash -> owner
        if (others.nonEmpty) collisions = collisions.updated(hash, others)
      }
      i = end
    }
    new ConsistentHash(ring.result(), collisions, virtualNodesFactor)
  }

  /**
   * Java API: Factory method to create a ConsistentHash
   */
  def create[T](nodes: java.lang.Iterable[T], virtualNodesFactor: Int): ConsistentHash[T] = {
    import scala.jdk.CollectionConverters._
    implicit val ct: ClassTag[T] = ClassTag.Any.asInstanceOf[ClassTag[T]]
    apply(nodes.asScala, virtualNodesFactor)
  }

  // nodes are identified by their toString, see the class documentation
  private def sameNode[T](a: T, b: T): Boolean = a.toString == b.toString

  // all nodes with a virtual node at the given ring position, the owner first
  private def claimants[T](
      nodes: immutable.SortedMap[Int, T],
      collisions: immutable.Map[Int, List[T]],
      hash: Int): List[T] =
    nodes.get(hash) match {
      case Some(owner) => owner :: collisions.getOrElse(hash, Nil)
      case None        => Nil
    }

  private def setClaimants[T](
      nodes: immutable.SortedMap[Int, T],
      collisions: immutable.Map[Int, List[T]],
      hash: Int,
      claimants: List[T]): (immutable.SortedMap[Int, T], immutable.Map[Int, List[T]]) =
    if (claimants.isEmpty) (nodes - hash, collisions - hash)
    else {
      val (owner, others) = ownerOf(claimants)
      (nodes.updated(hash, owner), if (others.isEmpty) collisions - hash else collisions.updated(hash, others))
    }

  // the lowest toString owns the position, independent of the order the nodes were added
  private def ownerOf[T](claimants: List[T]): (T, List[T]) =
    claimants match {
      case single :: Nil => (single, Nil)
      case _             =>
        val sorted = claimants.sortBy(_.toString)
        (sorted.head, sorted.tail)
    }

  // adds the virtual nodes of `node`, replacing any previous virtual nodes of the same node
  private def claim[T](
      nodes: immutable.SortedMap[Int, T],
      collisions: immutable.Map[Int, List[T]],
      node: T,
      virtualNodesFactor: Int): (immutable.SortedMap[Int, T], immutable.Map[Int, List[T]]) = {
    val nodeHash = hashFor(node.toString)
    (1 to virtualNodesFactor).foldLeft((nodes, collisions)) {
      case ((ns, cs), r) =>
        val hash = concatenateNodeHash(nodeHash, r)
        val others = claimants(ns, cs, hash).filterNot(sameNode(_, node))
        setClaimants(ns, cs, hash, node :: others)
    }
  }

  private def concatenateNodeHash(nodeHash: Int, vnode: Int): Int = {
    import MurmurHash._
    var h = startHash(nodeHash)
    h = extendHash(h, vnode, startMagicA, startMagicB)
    finalizeHash(h)
  }

  private def hashFor(bytes: Array[Byte]): Int = MurmurHash.arrayHash(bytes)

  private def hashFor(string: String): Int = MurmurHash.stringHash(string)
}
