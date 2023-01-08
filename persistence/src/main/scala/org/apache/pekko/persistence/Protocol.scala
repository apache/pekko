/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.persistence

import org.apache.pekko.actor.NoSerializationVerificationNeeded

/**
 * INTERNAL API.
 *
 * Messages exchanged between persistent actors and a journal/snapshot-store.
 */
private[persistence] object Protocol {

  /**
   * INTERNAL API.
   *
   * Internal persistence extension messages extend this trait.
   *
   * Helps persistence plugin developers to differentiate
   * internal persistence extension messages from their custom plugin messages.
   *
   * Journal messages need not be serialization verified as the Journal Actor
   * should always be a local Actor (and serialization is performed by plugins).
   * One notable exception to this is the shared journal used for testing.
   */
  trait Message extends NoSerializationVerificationNeeded

}
