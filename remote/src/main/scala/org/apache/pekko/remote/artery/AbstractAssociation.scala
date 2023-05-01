/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery

import org.apache.pekko.util.Unsafe

trait AbstractAssociation {
  protected var sharedStateOffset = 0L

  try sharedStateOffset =
      Unsafe.instance.objectFieldOffset(classOf[Association].getDeclaredField("_sharedStateDoNotCallMeDirectly"))
  catch {
    case t: Throwable =>
      throw new ExceptionInInitializerError(t)
  }

}

object AbstractAssociation extends AbstractAssociation
