/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2023 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.persistence.state

import com.typesafe.config.Config

import org.apache.pekko
import pekko.Done
import pekko.actor.ExtendedActorSystem
import pekko.persistence.state.{ DurableStateStoreProvider, DurableStateStoreRegistry }
import pekko.persistence.state.javadsl.{ DurableStateStore => JDurableStateStore }
import pekko.persistence.state.scaladsl.{ DurableStateStore, DurableStateUpdateStore, GetObjectResult }

import scala.concurrent.Future

//#plugin-provider
class MyStateStoreProvider(system: ExtendedActorSystem, config: Config, cfgPath: String)
    extends DurableStateStoreProvider {

  /**
   * The `DurableStateStore` implementation for the Scala API.
   * This corresponds to the instance that is returned by [[DurableStateStoreRegistry#durableStateStoreFor]].
   */
  override def scaladslDurableStateStore(): DurableStateStore[Any] = new MyStateStore(system, config, cfgPath)

  /**
   * The `DurableStateStore` implementation for the Java API.
   * This corresponds to the instance that is returned by [[DurableStateStoreRegistry#getDurableStateStoreFor]].
   */
  override def javadslDurableStateStore(): JDurableStateStore[AnyRef] = new MyJavaStateStore(system, config, cfgPath)
}
//#plugin-provider

//#plugin-api
class MyStateStore[A](system: ExtendedActorSystem, config: Config, cfgPath: String) extends DurableStateUpdateStore[A] {

  /**
   * Will persist the latest state. If it's a new persistence id, the record will be inserted.
   *
   * In case of an existing persistence id, the record will be updated only if the revision
   * number of the incoming record is 1 more than the already existing record. Otherwise persist will fail.
   */
  override def upsertObject(persistenceId: String, revision: Long, value: A, tag: String): Future[Done] = ???

  /**
   * Deprecated. Use the deleteObject overload with revision instead.
   */
  override def deleteObject(persistenceId: String): Future[Done] = deleteObject(persistenceId, 0)

  /**
   * Will delete the state by setting it to the empty state and the revision number will be incremented by 1.
   */
  override def deleteObject(persistenceId: String, revision: Long): Future[Done] = ???

  /**
   * Returns the current state for the given persistence id.
   */
  override def getObject(persistenceId: String): Future[GetObjectResult[A]] = ???
}
//#plugin-api
