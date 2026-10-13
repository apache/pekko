/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.util

import org.apache.pekko.annotation.InternalApi

/**
 * INTERNAL API
 *
 * Glob matching with the same semantics as [[Helpers.makePattern]] — `?` matches one
 * character, `*` matches any run of characters, everything else is literal — without
 * going through a regular expression.
 *
 * The regular expression `makePattern` builds turns every `*` into `.*`, and a chain of
 * those backtracks: matching `*a*a*a*a*a*a*a*a*a*a*a*a*b`, 26 characters, against a
 * 36 character name that cannot satisfy the trailing literal takes tens of seconds on one
 * thread, because the engine tries every way of distributing the literals. Actor selections
 * carry their pattern in the message, so that cost is reachable from a single small message.
 *
 * This matcher never revisits a decision more than once per input position, so it runs in
 * time proportional to the product of the two lengths at worst, and linearly in practice.
 */
@InternalApi private[pekko] object Glob {

  /**
   * True if `input` matches the glob `pattern`.
   */
  def matches(pattern: String, input: String): Boolean = {
    var p = 0 // next character of the pattern to match
    var i = 0 // next character of the input to match
    // where to resume from if the run consumed by the most recent `*` turns out to be too short
    var starP = -1
    var starI = -1

    while (i < input.length) {
      if (p < pattern.length && (pattern.charAt(p) == '?' || pattern.charAt(p) == input.charAt(i))) {
        p += 1
        i += 1
      } else if (p < pattern.length && pattern.charAt(p) == '*') {
        // remember where to come back to, and start by having the `*` consume nothing
        starP = p
        starI = i
        p += 1
      } else if (starP >= 0) {
        // let the most recent `*` consume one more character and try again from there
        starI += 1
        i = starI
        p = starP + 1
      } else {
        return false
      }
    }

    // trailing `*`s may match nothing, anything else left over means no match
    while (p < pattern.length && pattern.charAt(p) == '*') p += 1
    p == pattern.length
  }
}
