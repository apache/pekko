/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.serialization.jackson

/**
 * Predefined marker trait for serialization with Jackson CBOR.
 *
 * Messages, events and snapshots that extend/implement this marker are serialized with the
 * `jackson-cbor` serializer, since it is bound in `pekko.actor.serialization-bindings`
 * in the `reference.conf` of this module.
 */
trait CborSerializable
