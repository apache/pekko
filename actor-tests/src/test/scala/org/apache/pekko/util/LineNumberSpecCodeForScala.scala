/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

/*
 * IMPORTANT: do not change this file, the line numbers are verified in LineNumberSpec!
 */

object LineNumberSpecCodeForScala {

  val oneline = (s: String) => println(s)

  val twoline = (s: String) => {
    println(s)
    Integer.parseInt(s)
  }

  val partial: PartialFunction[String, Unit] = {
    case "a" =>
    case "b" =>
  }

  def method(s: String) = () => {
    println(s)
    Integer.parseInt(s)
  }
}
