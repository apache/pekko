/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.actor

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations.{ Scope => JmhScope }
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup

/*
[info] Benchmark                                            Mode   Samples        Score  Score error    Units
[info] a.a.ActorPathValidationBenchmark.handLoop7000       thrpt        20        0.070        0.002   ops/us
[info] a.a.ActorPathValidationBenchmark.old7000            -- blows up (stack overflow) --

[info] a.a.ActorPathValidationBenchmark.handLoopActor_1    thrpt        20       38.825        3.378   ops/us
[info] a.a.ActorPathValidationBenchmark.oldActor_1         thrpt        20        1.585        0.090   ops/us

`polluted = true` first exercises String.charAt with UTF-16 strings so its Latin-1/UTF-16 coder branch
profile (collected once, JVM-wide, in String.charAt's own bytecode) sees both coders, as it would in
any process that ever handles non-ASCII text. charAt-based scanners then get both coders compiled into
their loop; the byte-table scanner (isValidPathElement) is unaffected. For very long names the
byte-table scanner pays for the getBytes copy and is slower than an unpolluted charAt loop
(-f 3 -wi 5 -w 2s -i 10 -r 2s):

[info] Benchmark                                       (polluted)   Mode  Cnt    Score   Error   Units
[info] ActorPathValidationBenchmark.charAtLoop7000          false  thrpt   30    0.152 ± 0.003  ops/us
[info] ActorPathValidationBenchmark.charAtLoop7000           true  thrpt   30    0.086 ± 0.004  ops/us
[info] ActorPathValidationBenchmark.charAtLoopActor_1       false  thrpt   30  108.294 ± 4.260  ops/us
[info] ActorPathValidationBenchmark.charAtLoopActor_1        true  thrpt   30   41.834 ± 0.544  ops/us
[info] ActorPathValidationBenchmark.handLoop7000            false  thrpt   30    0.101 ± 0.014  ops/us
[info] ActorPathValidationBenchmark.handLoop7000             true  thrpt   30    0.096 ± 0.007  ops/us
[info] ActorPathValidationBenchmark.handLoopActor_1         false  thrpt   30  160.988 ± 6.836  ops/us
[info] ActorPathValidationBenchmark.handLoopActor_1          true  thrpt   30  161.953 ± 3.548  ops/us
 */
@Fork(2)
@State(JmhScope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Warmup(iterations = 5)
@Measurement(iterations = 10)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
class ActorPathValidationBenchmark {

  @Param(Array("false", "true"))
  var polluted: Boolean = false

  final val a = "actor-1"
  final val s = "687474703a2f2f74686566727569742e636f6d2f26683d37617165716378357926656e" * 100

  final val ElementRegex = """(?:[-\w:@&=+,.!~*'_;]|%\p{XDigit}{2})(?:[-\w:@&=+,.!~*'$_;]|%\p{XDigit}{2})*""".r

  @Setup
  def setup(): Unit = {
    if (polluted) {
      val utf16 = "årsrapport-ünïcödé-中文-é"
      var acc = 0
      var i = 0
      while (i < 2000000) {
        acc += (if (charAtLoop(utf16 + i)) 1 else 0) + utf16.charAt(i % utf16.length)
        i += 1
      }
      if (acc == 42) println("unlikely")
    }
  }

  //  @Benchmark // blows up with stack overflow, we know
  def old7000: Option[List[String]] = ElementRegex.unapplySeq(s)

  @Benchmark
  def handLoop7000: Boolean = ActorPath.isValidPathElement(s)

  @Benchmark
  def charAtLoop7000: Boolean = charAtLoop(s)

  @Benchmark
  def oldActor_1: Option[List[String]] = ElementRegex.unapplySeq(a)

  @Benchmark
  def handLoopActor_1: Boolean = ActorPath.isValidPathElement(a)

  @Benchmark
  def charAtLoopActor_1: Boolean = charAtLoop(a)

  // the String.charAt based validator that isValidPathElement used before the byte-table version
  private final val ValidSymbols = """-_.*$+:@&=,!~';"""

  private def charAtLoop(s: String): Boolean =
    if (s.isEmpty) false
    else {
      def isValidChar(c: Char): Boolean =
        (c >= 'a' && c <= 'z') ||
        (c >= 'A' && c <= 'Z') ||
        (c >= '0' && c <= '9') ||
        (ValidSymbols.indexOf(c) != -1)

      def isHexChar(c: Char): Boolean =
        (c >= 'a' && c <= 'f') ||
        (c >= 'A' && c <= 'F') ||
        (c >= '0' && c <= '9')

      val len = s.length
      def validate(pos: Int): Int =
        if (pos < len)
          s.charAt(pos) match {
            case c if isValidChar(c)                                                                  => validate(pos + 1)
            case '%' if pos + 2 < len && isHexChar(s.charAt(pos + 1)) && isHexChar(s.charAt(pos + 2)) =>
              validate(pos + 3)
            case _ => pos
          }
        else -1

      (if (len > 0 && s.charAt(0) != '$') validate(0) else 0) == -1
    }

}
