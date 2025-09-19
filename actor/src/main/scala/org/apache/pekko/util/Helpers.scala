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

//The 'Requiring' class is derived from scala-library code <https://github.com/scala/scala>.
/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package org.apache.pekko.util

import java.time.{ Instant, LocalDateTime, ZoneId }
import java.time.format.DateTimeFormatter
import java.util.{ Comparator, Locale }
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

import scala.annotation.tailrec
import scala.concurrent.duration.{ Duration, FiniteDuration }

import com.typesafe.config.{ Config, ConfigRenderOptions }

object Helpers {

  def toRootLowerCase(s: String): String = s.toLowerCase(Locale.ROOT)

  val isWindows: Boolean = toRootLowerCase(System.getProperty("os.name", "")).indexOf("win") >= 0

  def makePattern(s: String): Pattern =
    Pattern.compile("^\\Q" + s.replace("?", "\\E.\\Q").replace("*", "\\E.*\\Q") + "\\E$")

  def compareIdentityHash(a: AnyRef, b: AnyRef): Int = {
    /*
     * make sure that there is no overflow or underflow in comparisons, so
     * that the ordering is actually consistent and you cannot have a
     * sequence which cyclically is monotone without end.
     */
    val diff = (System.identityHashCode(a) & 0xFFFFFFFFL) - (System.identityHashCode(b) & 0xFFFFFFFFL)
    if (diff > 0) 1 else if (diff < 0) -1 else 0
  }

  /**
   * Create a comparator which will efficiently use `System.identityHashCode`,
   * unless that happens to be the same for two non-equals objects, in which
   * case the supplied “real” comparator is used; the comparator must be
   * consistent with equals, otherwise it would not be an enhancement over
   * the identityHashCode.
   */
  def identityHashComparator[T <: AnyRef](comp: Comparator[T]): Comparator[T] = new Comparator[T] {
    def compare(a: T, b: T): Int = compareIdentityHash(a, b) match {
      case 0 if a != b => comp.compare(a, b)
      case x           => x
    }
  }

  /**
   * Converts a "currentTimeMillis"-obtained timestamp accordingly:
   * {{{
   *   "$hours%02d:$minutes%02d:$seconds%02d.$ms%03dUTC"
   * }}}
   *
   * @param timestamp a "currentTimeMillis"-obtained timestamp
   * @return the formatted timestamp
   */
  def currentTimeMillisToUTCString(timestamp: Long): String = {
    val timeOfDay = timestamp % 86400000L
    val hours = timeOfDay / 3600000L
    val minutes = timeOfDay / 60000L % 60
    val seconds = timeOfDay / 1000L % 60
    val ms = timeOfDay % 1000

    // Initial capacity is "23:59:59.999UTC".length = 15
    val sb = new java.lang.StringBuilder(15)
    if (hours < 10) sb.append(0)
    sb.append(hours)
    sb.append(':')

    if (minutes < 10) sb.append(0)
    sb.append(minutes)
    sb.append(':')

    if (seconds < 10) sb.append(0)
    sb.append(seconds)
    sb.append('.')

    if (ms < 100) sb.append(0)
    if (ms < 10) sb.append(0)
    sb.append(ms)
    sb.append("UTC")
    sb.toString
  }

  private val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss.SSS")
  private val timeZone = ZoneId.systemDefault()

  def timestamp(time: Long): String = {
    formatter.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(time), timeZone))
  }

  final val base64chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+~"

  @tailrec
  def base64(l: Long, sb: java.lang.StringBuilder = new java.lang.StringBuilder("$")): String = {
    sb.append(base64chars.charAt(l.toInt & 63))
    val next = l >>> 6
    if (next == 0) sb.toString
    else base64(next, sb)
  }

  /**
   * Implicit class providing `requiring` methods. This class is based on
   * `Predef.ensuring` in the Scala standard library. The difference is that
   * this class's methods throw `IllegalArgumentException`s rather than
   * `AssertionError`s.
   *
   * An example adapted from `Predef`'s documentation:
   * {{{
   * import org.apache.pekko.util.Helpers.Requiring
   *
   * def addNaturals(nats: List[Int]): Int = {
   *   require(nats forall (_ >= 0), "List contains negative numbers")
   *   nats.foldLeft(0)(_ + _)
   * } requiring(_ >= 0)
   * }}}
   *
   * @param value The value to check.
   */
  final implicit class Requiring[A](val value: A) extends AnyVal {

    /**
     * Check that a condition is true. If true, return `value`, otherwise throw
     * an `IllegalArgumentException` with the given message.
     *
     * @param cond The condition to check.
     * @param msg The message to report if the condition isn't met.
     */
    def requiring(cond: Boolean, msg: => Any): A = {
      require(cond, msg)
      value
    }

    /**
     * Check that a condition is true for the `value`. If true, return `value`,
     * otherwise throw an `IllegalArgumentException` with the given message.
     *
     * @param cond The function used to check the `value`.
     * @param msg The message to report if the condition isn't met.
     */
    def requiring(cond: A => Boolean, msg: => Any): A = {
      require(cond(value), msg)
      value
    }
  }

  /**
   * INTERNAL API
   */
  private[pekko] final implicit class ConfigOps(val config: Config) extends AnyVal {
    def getMillisDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.MILLISECONDS)

    def getNanosDuration(path: String): FiniteDuration = getDuration(path, TimeUnit.NANOSECONDS)

    /**
     * Used to redact sensitive information in config data when we are logging it
     * or adding it to exception messages.
     *
     * This includes redacting environment variable values and the username associated with the running process.
     *
     * @return redacted version of the configuration text
     * @see https://github.com/apache/pekko/pull/771
     * @since 1.0.2
     */
    def renderWithRedactions(): String = {
      val username = System.getProperty("user.name")
      val configText = config.root.render(ConfigRenderOptions.defaults().setShowEnvVariableValues(false))
      configText.replace(username, "<username>")
    }

    private def getDuration(path: String, unit: TimeUnit): FiniteDuration =
      Duration(config.getDuration(path, unit), unit)
  }

}
