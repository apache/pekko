/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.LineNumbers._

class LineNumberSpec extends PekkoSpec {

  "LineNumbers" when {

    "writing Scala" must {
      import LineNumberSpecCodeForScala._

      "work for small functions" in {
        LineNumbers(oneline) should ===(SourceFileLines("LineNumberSpecCodeForScala.scala", 22, 22))
      }

      "work for larger functions" in {
        val result = LineNumbers(twoline)
        result should ===(SourceFileLines("LineNumberSpecCodeForScala.scala", 24, 24))
      }

      "work for partial functions" in {
        LineNumbers(partial) should ===(SourceFileLines("LineNumberSpecCodeForScala.scala", 29, 31))
      }

      "work for `def`" in {
        val result = LineNumbers(method("foo"))
        result should ===(SourceFileLines("LineNumberSpecCodeForScala.scala", 35, 36))
      }

    }

    "writing Java" must {
      val l = new LineNumberSpecCodeForJava

      "work for small functions" in {
        // because how java Lambdas are implemented/designed
        LineNumbers(l.f1()) should ===(SourceFileLines("LineNumberSpecCodeForJava.java", 29, 29))
      }

      "work for larger functions" in {
        // because how java Lambdas are implemented/designed
        LineNumbers(l.f2()) should ===(SourceFileLines("LineNumberSpecCodeForJava.java", 34, 35))
      }

      "work for anonymous classes" in {
        LineNumbers(l.f3()) should ===(SourceFileLines("LineNumberSpecCodeForJava.java", 40, 45))
      }

    }

  }

}
