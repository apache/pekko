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
