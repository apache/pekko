/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson.v2b

import jdoc.org.apache.pekko.serialization.jackson.MySerializable

// #add-mandatory
case class ItemAdded(shoppingCartId: String, productId: String, quantity: Int, discount: Double) extends MySerializable
// #add-mandatory
