/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.source

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source

object Unfold {

  // #countdown
  def countDown(from: Int): Source[Int, NotUsed] =
    Source.unfold(from) { current =>
      if (current == 0) None
      else Some((current - 1, current))
    }
  // #countdown

  // #fibonacci
  def fibonacci: Source[BigInt, NotUsed] =
    Source.unfold((BigInt(0), BigInt(1))) {
      case (a, b) =>
        Some(((b, a + b), a))
    }
  // #fibonacci

}
