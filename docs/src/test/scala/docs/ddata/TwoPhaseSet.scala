/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.ddata

import org.apache.pekko.cluster.ddata.ReplicatedData
import org.apache.pekko.cluster.ddata.GSet

//#twophaseset
case class TwoPhaseSet(adds: GSet[String] = GSet.empty, removals: GSet[String] = GSet.empty) extends ReplicatedData {
  type T = TwoPhaseSet

  def add(element: String): TwoPhaseSet =
    copy(adds = adds.add(element))

  def remove(element: String): TwoPhaseSet =
    copy(removals = removals.add(element))

  def elements: Set[String] = adds.elements.diff(removals.elements)

  override def merge(that: TwoPhaseSet): TwoPhaseSet =
    copy(adds = this.adds.merge(that.adds), removals = this.removals.merge(that.removals))
}
//#twophaseset
