/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.stream.impl.fusing

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStage, GraphStageLogic, InHandler, OutHandler }
import pekko.stream.{ Attributes, FlowShape, Inlet, Outlet }
import pekko.util.OptionVal

import scala.collection.immutable

/**
 * INTERNAL API
 */
@InternalApi
private[pekko] final case class GroupedAdjacentByWeighted[T, R](
    f: T => R,
    maxWeight: Long,
    costFn: T => Long)
    extends GraphStage[FlowShape[T, immutable.Seq[T]]] {

  require(f != null, "f must not be null")
  require(maxWeight > 0, "maxWeight must be greater than 0")
  require(costFn != null, "costFn must not be null")

  private val in = Inlet[T]("GroupedAdjacentByWeighted.in")
  private val out = Outlet[immutable.Seq[T]]("GroupedAdjacentByWeighted.out")

  override val shape: FlowShape[T, immutable.Seq[T]] = FlowShape(in, out)
  override def initialAttributes: Attributes = DefaultAttributes.groupedAdjacentByWeighted

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) with InHandler with OutHandler {
      private var builder = Vector.newBuilder[T]
      private var currentWeight: Long = 0L
      // used to track if elements has been added to the current group, zero weight is allowed
      private var hasElements: Boolean = false
      private var currentKey: OptionVal[R] = OptionVal.none
      private var pendingGroup: OptionVal[immutable.Seq[T]] = OptionVal.none

      override def onPush(): Unit = {
        val elem = grab(in)
        val cost = costFn(elem)

        if (cost < 0L) {
          failStage(new IllegalArgumentException(s"Negative weight [$cost] for element [$elem] is not allowed"))
          return
        }

        val elemKey = f(elem)
        require(elemKey != null, "Element key must not be null")

        if (shouldPushDirectly(cost)) {
          push(out, Vector(elem))
        } else if (shouldStartNewGroup(elemKey, cost)) {
          emitCurrentGroup()
          handleNewElement(elem, cost, elemKey)
        } else {
          addToCurrentGroup(elem, cost, elemKey)
          tryPullIfNeeded()
        }
      }

      private def shouldPushDirectly(cost: Long): Boolean = {
        cost >= maxWeight && !hasElements
      }

      private def shouldStartNewGroup(elemKey: R, cost: Long): Boolean = currentKey match {
        case OptionVal.Some(key) if (elemKey != key) || (currentWeight + cost > maxWeight) => true
        case OptionVal.None if cost > maxWeight                                            => true
        case _                                                                             => false
      }

      private def emitCurrentGroup(): Unit = if (hasElements) {
        val group = builder.result()
        resetGroup()
        pushOrQueue(group)
      }

      private def handleNewElement(elem: T, cost: Long, key: R): Unit = {
        if (cost > maxWeight) {
          pushOrQueue(Vector(elem))
        } else {
          addToCurrentGroup(elem, cost, key)
        }
        tryPullIfNeeded()
      }

      private def addToCurrentGroup(elem: T, cost: Long, key: R): Unit = {
        builder += elem
        hasElements = true
        currentWeight += cost
        currentKey = OptionVal.Some(key)
      }

      private def resetGroup(): Unit = {
        builder.clear()
        hasElements = false
        currentWeight = 0L
        currentKey = OptionVal.none
      }

      private def pushOrQueue(group: immutable.Seq[T]): Unit = pendingGroup match {
        case OptionVal.Some(pending) =>
          push(out, pending)
          pendingGroup = OptionVal.Some(group)
        case OptionVal.None =>
          if (isAvailable(out)) {
            push(out, group)
          } else {
            pendingGroup = OptionVal.Some(group)
          }
      }

      private def tryPullIfNeeded(): Unit = pendingGroup match {
        case OptionVal.None if !hasBeenPulled(in) && isAvailable(out) => pull(in)
        case _                                                        =>
      }

      override def onPull(): Unit = {
        pendingGroup match {
          case OptionVal.Some(group) =>
            push(out, group)
            pendingGroup = OptionVal.none
          case _ => if (!hasBeenPulled(in)) pull(in)
        }
      }

      override def onUpstreamFinish(): Unit = {
        val finalGroup = builder.result()
        builder = null

        pendingGroup match {
          case OptionVal.Some(group) =>
            if (finalGroup.nonEmpty) {
              emitMultiple(out, List(group, finalGroup).iterator, () => completeStage())
            } else {
              emit(out, group, () => completeStage())
            }
          case OptionVal.None =>
            if (finalGroup.nonEmpty) {
              emit(out, finalGroup, () => completeStage())
            } else {
              completeStage()
            }
        }
      }

      setHandlers(in, out, this)
    }
}
