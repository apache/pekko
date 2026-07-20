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

package org.apache.pekko.actor.dungeon

import org.apache.pekko.actor.Identify
import org.apache.pekko.actor.NotInfluenceReceiveTimeout

private[pekko] object ReceiveTimeoutCompat {

  // Identify is also an AutoReceivedMessage. Checking its concrete class first avoids polluting its secondary
  // supertype cache on JDKs affected by JDK-8180450 when actor runtime code also checks the AutoReceivedMessage marker.
  inline def isNotInfluenceReceiveTimeout(message: Any): Boolean =
    message.isInstanceOf[Identify] || message.isInstanceOf[NotInfluenceReceiveTimeout]
}
