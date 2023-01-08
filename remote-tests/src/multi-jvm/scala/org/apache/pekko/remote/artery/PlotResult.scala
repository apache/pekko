/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

final case class PlotResult(values: Vector[(String, Number)] = Vector.empty) {

  def add(key: String, value: Number): PlotResult =
    copy(values = values :+ (key -> value))

  def addAll(p: PlotResult): PlotResult =
    copy(values ++ p.values)

  def csvLabels: String = values.map(_._1).mkString("\"", "\",\"", "\"")

  def csvValues: String = values.map(_._2).mkString("\"", "\",\"", "\"")

  // this can be split to two lines with bash: cut -d':' -f2,3 | tr ':' $'\n'
  def csv(name: String): String = s"PLOT_${name}:${csvLabels}:${csvValues}"

}

final case class LatencyPlots(
    plot50: PlotResult = PlotResult(),
    plot90: PlotResult = PlotResult(),
    plot99: PlotResult = PlotResult())
