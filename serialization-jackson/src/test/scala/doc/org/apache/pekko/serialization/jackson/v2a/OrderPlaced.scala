/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2a

import doc.org.apache.pekko.serialization.jackson.MySerializable

// #rename-class
case class OrderPlaced(shoppingCartId: String) extends MySerializable
// #rename-class
