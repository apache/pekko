/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class PekkoVersionSpec extends AnyWordSpec with Matchers {

  "The Pekko version check" must {

    "succeed if version is ok" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.6")
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.7")
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.6.0")
    }

    "succeed if version is RC and ok" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.7-RC10")
      PekkoVersion.require("PekkoVersionSpec", "2.6.0-RC1", "2.6.0-RC1")
    }

    "fail if version is RC and not ok" in {
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.6-RC1")
      }
    }

    "succeed if version is milestone and ok" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.7-M10")
    }

    "fail if version is milestone and not ok" in {
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.6-M1")
      }
    }

    "fail if major version is different" in {
      // because not bincomp
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "3.0.0")
      }
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "1.0.0")
      }
    }

    "fail if minor version is too low" in {
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.4.19")
      }
    }

    "fail if patch version is too low" in {
      intercept[UnsupportedPekkoVersion] {
        PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5.5")
      }
    }

    "succeed if current Pekko version is SNAPSHOT" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5-SNAPSHOT")
    }

    "succeed if current Pekko version is timestamped SNAPSHOT" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5.6", "2.5-20180109-133700")
    }

    "succeed if required Pekko version is SNAPSHOT" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5-SNAPSHOT", "2.5-SNAPSHOT")
    }

    "succeed if required Pekko version is timestamped SNAPSHOT" in {
      PekkoVersion.require("PekkoVersionSpec", "2.5-20180109-133700", "2.5-20180109-133700")
    }

    "silently comply if current version is incomprehensible" in {
      // because we may want to release with weird numbers for some reason
      PekkoVersion.require("nonsense", "2.5.6", "nonsense")
    }

  }

}
