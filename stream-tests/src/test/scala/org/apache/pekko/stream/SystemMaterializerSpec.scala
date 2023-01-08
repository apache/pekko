/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream

import scala.concurrent.Future

import org.scalatest.concurrent.ScalaFutures

import org.apache.pekko
import pekko.stream.scaladsl.Keep
import pekko.stream.scaladsl.Sink
import pekko.stream.scaladsl.Source
import pekko.stream.testkit.StreamSpec

class SystemMaterializerSpec extends StreamSpec with ScalaFutures {

  def compileOnly(): Unit = {
    Source(1 to 3).to(Sink.ignore).run()
    Source(1 to 3).runWith(Sink.ignore)
    Source(1 to 3).runFold(0)((acc, elem) => acc + elem)
    Source(1 to 3).runFoldAsync(0)((acc, elem) => Future.successful(acc + elem))
    Source(1 to 3).runForeach(_ => ())
    Source(1 to 3).runReduce(_ + _)
  }

  "The SystemMaterializer" must {

    "be implicitly provided when implicit actor system is in scope" in {
      val result = Source(1 to 3).toMat(Sink.seq)(Keep.right).run()
      result.futureValue should ===(Seq(1, 2, 3))
    }
  }

}

class SystemMaterializerEagerStartupSpec extends StreamSpec {

  "The SystemMaterializer" must {

    "be eagerly started on system startup" in {
      system.hasExtension(SystemMaterializer.lookup) should ===(true)
    }
  }

}
