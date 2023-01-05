/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2a

import doc.org.apache.pekko.serialization.jackson.MySerializable

// #structural
case class Customer(name: String, shippingAddress: Address, billingAddress: Option[Address]) extends MySerializable
// #structural
