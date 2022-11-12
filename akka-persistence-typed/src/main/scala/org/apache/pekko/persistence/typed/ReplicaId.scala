/*
 * Copyright (C) 2020-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence.typed

/**
 * Identifies a replica in Replicated Event Sourcing, could be a datacenter name or a logical identifier.
 */
final case class ReplicaId(id: String)
