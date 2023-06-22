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

package docs.org.apache.pekko.typed

//#loggerops-package-implicit
import scala.language.implicitConversions
import org.apache.pekko.actor.typed.scaladsl.LoggerOps
import org.slf4j.Logger

package object myapp {

  implicit def loggerOps(logger: Logger): LoggerOps =
    LoggerOps(logger)

}
//#loggerops-package-implicit
