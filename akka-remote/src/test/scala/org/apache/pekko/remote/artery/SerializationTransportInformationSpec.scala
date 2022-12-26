/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko.remote.serialization.AbstractSerializationTransportInformationSpec

class SerializationTransportInformationSpec
    extends AbstractSerializationTransportInformationSpec(ArterySpecSupport.defaultConfig)
