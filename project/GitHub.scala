/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2016-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

object GitHub {

  def envTokenOrThrow: Option[String] =
    sys.env.get("PR_VALIDATOR_GH_TOKEN").orElse {
      if (sys.env.contains("ghprbPullId")) {
        throw new Exception(
          "No PR_VALIDATOR_GH_TOKEN env var provided during GitHub Pull Request Builder build, unable to reach GitHub!")
      } else {
        None
      }
    }

  def url(v: String): String = {
    val branch = if (v.endsWith("SNAPSHOT")) "main" else "v" + v
    "https://github.com/apache/incubator-pekko/tree/" + branch
  }
}
