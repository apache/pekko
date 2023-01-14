/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package jdoc.org.apache.pekko.serialization.jackson.v2a;

import jdoc.org.apache.pekko.serialization.jackson.MySerializable;

import java.util.Optional;

// #structural
public class Customer implements MySerializable {
  public final String name;
  public final Address shippingAddress;
  public final Optional<Address> billingAddress;

  public Customer(String name, Address shippingAddress, Optional<Address> billingAddress) {
    this.name = name;
    this.shippingAddress = shippingAddress;
    this.billingAddress = billingAddress;
  }
}
// #structural
