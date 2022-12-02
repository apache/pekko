/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package docs.stream.operators.sourceorflow

import org.apache.pekko.stream.scaladsl.Flow
//#logWithMarker
import org.apache.pekko
import pekko.event.LogMarker
import pekko.stream.Attributes

//#logWithMarker

object LogWithMarker {
  def logWithMarkerExample(): Unit = {
    Flow[String]
      // #logWithMarker
      .logWithMarker(name = "myStream", e => LogMarker(name = "myMarker", properties = Map("element" -> e)))
      .addAttributes(
        Attributes.logLevels(
          onElement = Attributes.LogLevels.Off,
          onFinish = Attributes.LogLevels.Info,
          onFailure = Attributes.LogLevels.Error))
    // #logWithMarker
  }
}
