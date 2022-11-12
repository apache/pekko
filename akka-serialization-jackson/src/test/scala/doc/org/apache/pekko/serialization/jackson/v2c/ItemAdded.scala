/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2c

import doc.org.apache.pekko.serialization.jackson.MySerializable

// #rename
case class ItemAdded(shoppingCartId: String, itemId: String, quantity: Int) extends MySerializable
// #rename
