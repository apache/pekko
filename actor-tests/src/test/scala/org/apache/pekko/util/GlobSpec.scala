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

import org.scalatest.concurrent.TimeLimits
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{ Seconds, Span }
import org.scalatest.wordspec.AnyWordSpec

class GlobSpec extends AnyWordSpec with Matchers with TimeLimits {

  private def allStrings(alphabet: Seq[Char], maxLength: Int): Seq[String] =
    (0 to maxLength).flatMap { n =>
      (1 to n).foldLeft(Seq("")) { (acc, _) =>
        acc.flatMap(s => alphabet.map(s + _))
      }
    }

  "Glob" must {

    "match the literal cases" in {
      Glob.matches("abc", "abc") should ===(true)
      Glob.matches("abc", "abd") should ===(false)
      Glob.matches("", "") should ===(true)
      Glob.matches("", "a") should ===(false)
      Glob.matches("a", "") should ===(false)
    }

    "treat ? as exactly one character" in {
      Glob.matches("a?c", "abc") should ===(true)
      Glob.matches("a?c", "ac") should ===(false)
      Glob.matches("a?c", "abbc") should ===(false)
      Glob.matches("???", "abc") should ===(true)
    }

    "treat * as any run of characters" in {
      Glob.matches("*", "") should ===(true)
      Glob.matches("*", "anything") should ===(true)
      Glob.matches("a*", "a") should ===(true)
      Glob.matches("a*c", "abbbc") should ===(true)
      Glob.matches("a*c", "abbbd") should ===(false)
      Glob.matches("**", "ab") should ===(true)
      Glob.matches("*b*", "abc") should ===(true)
    }

    "agree with the regular expression it replaces, exhaustively over short inputs" in {
      // Helpers.makePattern is what SelectChildPattern used before; matching has to be
      // unchanged, so compare the two over every short pattern and input.
      val patterns = allStrings(Seq('a', 'b', '*', '?'), 4)
      val inputs = allStrings(Seq('a', 'b'), 4)
      patterns.size should be > 300
      for {
        pattern <- patterns
        regex = Helpers.makePattern(pattern)
        input <- inputs
      } withClue(s"pattern [$pattern] input [$input]: ") {
        Glob.matches(pattern, input) should ===(regex.matcher(input).matches)
      }
    }

    "match a pattern that makes the regular expression backtrack, promptly" in {
      // 26 characters against a 36 character name. Through Helpers.makePattern this takes
      // roughly 47 seconds on one thread; here it is immediate.
      val pattern = ("*a" * 12) + "*b"
      val name = "a" * 36
      failAfter(Span(3, Seconds)) {
        Glob.matches(pattern, name) should ===(false)
      }
    }

    "stay prompt as the input grows" in {
      val pattern = ("*a" * 20) + "*b"
      failAfter(Span(5, Seconds)) {
        Glob.matches(pattern, "a" * 2000) should ===(false)
      }
    }
  }
}
