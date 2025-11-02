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

package docs.stream.operators

//#imports
import org.apache.pekko
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl._

//#imports

object MapOption {

  // #mapOption
  val source: Source[Int, NotUsed] = Source(1 to 10)
  val mapped: Source[String, NotUsed] = source.mapOption(elem => if (elem % 2 == 0) Some(elem.toString) else None)
  // #mapOption
}
