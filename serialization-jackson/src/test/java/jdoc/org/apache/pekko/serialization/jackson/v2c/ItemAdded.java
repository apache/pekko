/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.org.apache.pekko.serialization.jackson.v2c;

import jdoc.org.apache.pekko.serialization.jackson.MySerializable;

// #rename
public class ItemAdded implements MySerializable {
  public final String shoppingCartId;

  public final String itemId;

  public final int quantity;

  public ItemAdded(String shoppingCartId, String itemId, int quantity) {
    this.shoppingCartId = shoppingCartId;
    this.itemId = itemId;
    this.quantity = quantity;
  }
}
// #rename
