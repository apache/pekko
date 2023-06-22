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

package org.apache.pekko.testkit.metrics

trait MetricKeyDSL {

  case class MetricKey private[MetricKeyDSL] (path: String) {

    import MetricKey._

    def /(key: String): MetricKey = MetricKey(path + "." + sanitizeMetricKeyPart(key))

    override def toString = path
  }

  object MetricKey {
    def fromString(root: String) = MetricKey(sanitizeMetricKeyPart(root))

    // todo not sure what else needs replacing, while keeping key as readable as can be
    private def sanitizeMetricKeyPart(keyPart: String) =
      keyPart
        .replaceAll("""\.\.\.""", "\u2026") // ... => …
        .replaceAll("""\.""", "-")
        .replaceAll("""[\]\[\(\)\<\>]""", "|")
        .replaceAll(" ", "-")
        .replaceAll("/", "-")
  }

}

object MetricKeyDSL extends MetricKeyDSL
