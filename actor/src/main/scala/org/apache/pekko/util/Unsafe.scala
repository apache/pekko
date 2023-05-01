/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, derived from Akka.
 */

/*
 * Copyright (C) 2009-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import org.apache.pekko.annotation.InternalApi

import java.nio.charset.StandardCharsets
import java.util

/** INTERNAL API */

// This should ideally be package private under pekko
// but there is a bug in Scala 3.3.x which can only
// be fixed in a newer binary incompatible version
// of Scala 3, see https://github.com/lampepfl/dotty/issues/18646#issuecomment-1764398271
@InternalApi object Unsafe {
  var instance: sun.misc.Unsafe = _
  private var stringValueFieldOffset = 0L
  private var isJavaVersion9Plus = false
  private var copyUSAsciiStrToBytesAlgorithm = 0

  def isIsJavaVersion9Plus: Boolean = {
    // See Oracle section 1.5.3 at:
    // https://docs.oracle.com/javase/8/docs/technotes/guides/versioning/spec/versioning2.html
    val version =
      util.Arrays.stream(System.getProperty("java.specification.version").split("\\.")).mapToInt(
        Integer.parseInt).toArray
    val javaVersion = if (version(0) == 1)
      version(1)
    else
      version(0)
    javaVersion > 8
  }

  def testUSAsciiStrToBytesAlgorithm0(str: String): Boolean =
    try {
      val bytes = new Array[Byte](str.length)
      // copy of implementation in copyUSAciiBytesToStr
      val strBytes = str.getBytes(StandardCharsets.US_ASCII)
      System.arraycopy(strBytes, 0, bytes, 0, str.length)
      // end copy
      val result = copyUSAciiBytesToStr(str.length, bytes)
      str == result
    } catch {
      case _: Throwable =>
        false
    }

  def testUSAsciiStrToBytesAlgorithm1(str: String): Boolean =
    try {
      val bytes = new Array[Byte](str.length)
      // copy of implementation in copyUSAciiBytesToStr
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Byte]]
      System.arraycopy(chars, 0, bytes, 0, str.length)
      // end copy
      val result = copyUSAciiBytesToStr(str.length, bytes)
      str == result
    } catch {
      case _: Throwable =>
        false
    }
  def testUSAsciiStrToBytesAlgorithm2(str: String): Boolean =
    try {
      val bytes = new Array[Byte](str.length)
      // copy of implementation in copyUSAciiBytesToStr
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Char]]
      var i = 0
      while (i < str.length) bytes(i) = chars { i += 1; i - 1 }.toByte
      // end copy
      val result = copyUSAciiBytesToStr(str.length, bytes)
      str == result
    } catch {
      case _: Throwable =>
        false
    }

  private def copyUSAciiBytesToStr(length: Int, bytes: Array[Byte]) = {
    val resultChars = new Array[Char](length)
    var i = 0
    while (i < length) {
      // UsAscii
      resultChars(i) = bytes(i).toChar
      i += 1
    }
    String.valueOf(resultChars, 0, length)
  }

  def copyUSAsciiStrToBytes(str: String, bytes: Array[Byte]): Unit = {
    if (copyUSAsciiStrToBytesAlgorithm == 1) {
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Byte]]
      System.arraycopy(chars, 0, bytes, 0, str.length)
    } else if (copyUSAsciiStrToBytesAlgorithm == 2) {
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Char]]
      var i = 0
      while (i < str.length) bytes(i) = chars { i += 1; i - 1 }.toByte
    } else {
      val strBytes = str.getBytes(StandardCharsets.US_ASCII)
      System.arraycopy(strBytes, 0, bytes, 0, str.length)
    }
  }

  def fastHash(str: String): Int = {
    var s0 = 391408L
    var s1 = 601258L
    var i = 0
    if (copyUSAsciiStrToBytesAlgorithm == 1) {
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Byte]]
      while (i < str.length) {
        var x = s0 ^ chars { i += 1; i - 1 }.toLong // Mix character into PRNG state

        var y = s1
        // Xorshift128+ round
        s0 = y
        x ^= x << 23
        y ^= y >>> 26
        x ^= x >>> 17
        s1 = x ^ y
      }
    } else if (copyUSAsciiStrToBytesAlgorithm == 2) {
      val chars = instance.getObject(str, stringValueFieldOffset).asInstanceOf[Array[Char]]
      while (i < str.length) {
        var x = s0 ^ chars { i += 1; i - 1 }.toLong // Mix character into PRNG state

        var y = s1
        // Xorshift128+ round
        s0 = y
        x ^= x << 23
        y ^= y >>> 26
        x ^= x >>> 17
        s1 = x ^ y
      }
    } else {
      val chars = str.getBytes(StandardCharsets.US_ASCII)
      while (i < str.length) {
        var x = s0 ^ chars { i += 1; i - 1 }.toLong // Mix character into PRNG state

        var y = s1
        // Xorshift128+ round
        s0 = y
        x ^= x << 23
        y ^= y >>> 26
        x ^= x >>> 17
        s1 = x ^ y
      }
    }
    (s0 + s1).toInt
  }

  try {
    var found: sun.misc.Unsafe = null
    val fields = classOf[sun.misc.Unsafe].getDeclaredFields
    var i = 0
    while (i < fields.size && found == null) {
      val field = fields(i)
      if (field.getType eq classOf[sun.misc.Unsafe]) {
        field.setAccessible(true)
        found = field.get(null).asInstanceOf[sun.misc.Unsafe]
      }
      i += 1
    }
    if (found == null)
      throw new IllegalStateException("Can't find instance of sun.misc.Unsafe")
    else
      instance = found
    var fo = 0L
    try fo = instance.objectFieldOffset(classOf[String].getDeclaredField("value"))
    catch {
      case _: NoSuchFieldException =>
        // The platform's implementation of String doesn't have a 'value' field, so we have to use
        // algorithm 0
        fo = -1
    }
    stringValueFieldOffset = fo
    isJavaVersion9Plus = isIsJavaVersion9Plus
    if (stringValueFieldOffset > -1) {
      // Select optimization algorithm for `copyUSAciiBytesToStr`.
      // For example algorithm 1 will fail with JDK 11 on ARM32 (Raspberry Pi),
      // and therefore algorithm 0 is selected on that architecture.
      val testStr = "abc"
      if (isJavaVersion9Plus && testUSAsciiStrToBytesAlgorithm1(testStr))
        copyUSAsciiStrToBytesAlgorithm = 1
      else if (testUSAsciiStrToBytesAlgorithm2(testStr))
        copyUSAsciiStrToBytesAlgorithm = 2
      else
        copyUSAsciiStrToBytesAlgorithm = 0
    } else
      copyUSAsciiStrToBytesAlgorithm = 0 // We know so little about the platform's String implementation that we have
    // no choice but to select algorithm 0
  } catch {
    case t: Throwable =>
      throw new ExceptionInInitializerError(t)
  }

}
