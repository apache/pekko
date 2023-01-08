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

package test.org.apache.pekko.serialization

import org.apache.pekko.actor.NoSerializationVerificationNeeded

/**
 *  This is currently used in NoSerializationVerificationNeeded test cases in SerializeSpec,
 *  as they needed a serializable class whose top package is not pekko.
 */
class NoVerification extends NoSerializationVerificationNeeded with java.io.Serializable {}
