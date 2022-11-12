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
