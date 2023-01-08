/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.dispatch

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(2)
@Warmup(iterations = 10)
@Measurement(iterations = 20)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
class CachingConfigBenchmark {

  val deepKey = "pekko.actor.deep.settings.something"
  val deepConfigString = s"""$deepKey = something"""
  val deepConfig = ConfigFactory.parseString(deepConfigString)
  val deepCaching = new CachingConfig(deepConfig)

  @Benchmark def deep_config = deepConfig.hasPath(deepKey)
  @Benchmark def deep_caching = deepCaching.hasPath(deepKey)

}
