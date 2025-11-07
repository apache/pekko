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

package org.apache.pekko

import org.apache.pekko.annotation.InternalApi

final class UnsupportedPekkoVersion private[pekko] (msg: String) extends RuntimeException(msg)

object PekkoVersion {

  /**
   * Check that the version of Pekko is a specific patch version or higher and throw an [[UnsupportedPekkoVersion]]
   * exception if the version requirement is not fulfilled.
   *
   * For example: `require("my-library", "2.5.4")` would fail if used with Pekko 2.4.19 and 2.5.3, but succeed with 2.5.4
   * and 2.6.1
   *
   * @param libraryName The name of the library or component requiring the Pekko version, used in the error message.
   * @param requiredVersion Minimal version that this library works with
   */
  def require(libraryName: String, requiredVersion: String): Unit = {
    require(libraryName, requiredVersion, Version.current)
  }

  /**
   * Internal API:
   */
  @InternalApi
  private[pekko] def require(libraryName: String, requiredVersion: String, currentVersion: String): Unit = {
    if (requiredVersion != currentVersion) {
      val VersionPattern = """(\d+)\.(\d+)\.(\d+)(?:-(M|RC)(\d+))?""".r
      currentVersion match {
        case VersionPattern(currentMajorStr, currentMinorStr, currentPatchStr, mOrRc, mOrRcNrStr) =>
          requiredVersion match {
            case requiredVersion @ VersionPattern(
                  requiredMajorStr,
                  requiredMinorStr,
                  requiredPatchStr,
                  requiredMorRc,
                  requiredMOrRcNrStr) =>
              def throwUnsupported() =
                throw new UnsupportedPekkoVersion(
                  s"Current version of Pekko is [$currentVersion], but $libraryName requires version [$requiredVersion]")
              if (requiredMajorStr == currentMajorStr) {
                if (requiredMinorStr == currentMinorStr) {
                  if (requiredPatchStr == currentPatchStr) {
                    if (requiredMorRc == null && mOrRc != null)
                      throwUnsupported() // require final but actual is M or RC
                    else if (requiredMorRc != null) {
                      (requiredMorRc, requiredMOrRcNrStr, mOrRc, mOrRcNrStr) match {
                        case ("M", reqN, "M", n)   => if (reqN.toInt > n.toInt) throwUnsupported()
                        case ("M", _, "RC", _)     => // RC > M, ok!
                        case ("RC", _, "M", _)     => throwUnsupported()
                        case ("RC", reqN, "RC", n) => if (reqN.toInt > n.toInt) throwUnsupported()
                        case (_, _, null, _)       => // requirement on RC or M but actual is final, ok!
                        case unexpected            =>
                          throw new IllegalArgumentException(s"Should never happen, comparing M/RC: $unexpected")
                      }
                    } // same versions, ok!
                  } else if (requiredPatchStr.toInt > currentPatchStr.toInt) throwUnsupported()
                } else if (requiredMinorStr.toInt > currentMinorStr.toInt) throwUnsupported()
              } else throwUnsupported() // major diff
            case _ => // SNAPSHOT or unknown - you're on your own
          }

        case _ => // SNAPSHOT or unknown - you're on your own
      }
    }
  }

}
