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

package org.apache.pekko.persistence.state

/**
 * A durable state store plugin must implement a class that implements this trait.
 * It provides the concrete implementations for the Java and Scala APIs.
 *
 * A durable state store plugin plugin must provide implementations for both
 * `org.apache.pekko.persistence.state.scaladsl.DurableStateStore` and `org.apache.pekko.persistence.state.javadsl.DurableStateStore`.
 * One of the implementations can delegate to the other.
 */
trait DurableStateStoreProvider {

  /**
   * The `ReadJournal` implementation for the Scala API.
   * This corresponds to the instance that is returned by [[DurableStateStoreRegistry#durableStateStoreFor]].
   */
  def scaladslDurableStateStore(): scaladsl.DurableStateStore[Any]

  /**
   * The `DurableStateStore` implementation for the Java API.
   * This corresponds to the instance that is returned by [[DurableStateStoreRegistry#getDurableStateStoreFor]].
   */
  def javadslDurableStateStore(): javadsl.DurableStateStore[AnyRef]
}
