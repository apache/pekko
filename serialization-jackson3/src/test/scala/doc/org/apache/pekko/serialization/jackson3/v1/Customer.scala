/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package doc.org.apache.pekko.serialization.jackson3.v1

import doc.org.apache.pekko.serialization.jackson3.MySerializable

// #structural
case class Customer(name: String, street: String, city: String, zipCode: String, country: String) extends MySerializable
// #structural
