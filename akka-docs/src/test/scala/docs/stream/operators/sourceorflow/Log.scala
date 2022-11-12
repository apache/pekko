/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.stream.scaladsl.Flow
//#log
import org.apache.pekko.stream.Attributes

//#log

object Log {
  def logExample(): Unit = {
    Flow[String]
      // #log
      .log(name = "myStream")
      .addAttributes(
        Attributes.logLevels(
          onElement = Attributes.LogLevels.Off,
          onFinish = Attributes.LogLevels.Info,
          onFailure = Attributes.LogLevels.Error))
    // #log
  }
}
