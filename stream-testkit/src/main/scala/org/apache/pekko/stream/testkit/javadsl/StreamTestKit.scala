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

package org.apache.pekko.stream.testkit.javadsl

import org.apache.pekko
import pekko.actor.ClassicActorSystemProvider
import pekko.stream.{ Materializer, SystemMaterializer }
import pekko.stream.impl.PhasedFusingActorMaterializer
import pekko.stream.testkit.scaladsl

object StreamTestKit {

  /**
   * Assert that there are no stages running under a given materializer.
   * Usually this assertion is run after a test-case to check that all of the
   * stages have terminated successfully.
   */
  def assertAllStagesStopped(mat: Materializer): Unit =
    mat match {
      case impl: PhasedFusingActorMaterializer =>
        scaladsl.StreamTestKit.assertNoChildren(impl.system, impl.supervisor)
      case _ =>
    }

  /**
   * Assert that there are no stages running under a given system's materializer.
   * Usually this assertion is run after a test-case to check that all of the
   * stages have terminated successfully.
   */
  def assertAllStagesStopped(system: ClassicActorSystemProvider): Unit =
    assertAllStagesStopped(SystemMaterializer(system).materializer)
}
