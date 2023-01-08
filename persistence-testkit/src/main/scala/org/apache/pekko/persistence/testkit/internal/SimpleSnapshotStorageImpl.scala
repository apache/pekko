/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.testkit.internal

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.persistence.SnapshotMetadata
import pekko.persistence.testkit.SnapshotStorage

/**
 * INTERNAL API
 */
@InternalApi
private[testkit] class SimpleSnapshotStorageImpl extends SnapshotStorage {

  override type InternalRepr = (SnapshotMetadata, Any)

  override def toRepr(internal: (SnapshotMetadata, Any)): (SnapshotMetadata, Any) = identity(internal)

  override def toInternal(repr: (SnapshotMetadata, Any)): (SnapshotMetadata, Any) = identity(repr)

}
