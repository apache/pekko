/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko.remote.AbstractTransientSerializationErrorSpec

class TransientSerializationErrorSpec extends AbstractTransientSerializationErrorSpec(ArterySpecSupport.defaultConfig)
