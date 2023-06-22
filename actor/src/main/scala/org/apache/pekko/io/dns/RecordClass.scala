/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.io.dns

final case class RecordClass(code: Short, name: String)

object RecordClass {

  val IN = RecordClass(1, "IN")
  val CS = RecordClass(2, "CS")
  val CH = RecordClass(3, "CH")
  val HS = RecordClass(4, "HS")

  val WILDCARD = RecordClass(255, "WILDCARD")

}
