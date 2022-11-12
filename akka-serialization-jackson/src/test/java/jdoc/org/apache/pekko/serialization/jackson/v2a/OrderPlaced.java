/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.org.apache.pekko.serialization.jackson.v2a;

import com.fasterxml.jackson.annotation.JsonCreator;
import jdoc.org.apache.pekko.serialization.jackson.MySerializable;

// #rename-class
public class OrderPlaced implements MySerializable {
  public final String shoppingCartId;

  @JsonCreator
  public OrderPlaced(String shoppingCartId) {
    this.shoppingCartId = shoppingCartId;
  }
}
// #rename-class
