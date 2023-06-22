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

package docs.stream.cookbook

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl._
import org.apache.pekko.util.ByteString

class RecipeKeepAlive extends RecipeSpec {

  "Recipe for injecting keepalive messages" must {

    "work" in {
      val keepaliveMessage = ByteString(11)

      // #inject-keepalive
      import scala.concurrent.duration._
      val injectKeepAlive: Flow[ByteString, ByteString, NotUsed] =
        Flow[ByteString].keepAlive(1.second, () => keepaliveMessage)
      // #inject-keepalive

      // No need to test, this is a built-in stage with proper tests
    }
  }

}
