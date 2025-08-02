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

package org.apache.pekko.util;

import org.apache.pekko.annotation.InternalApi;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

/** INTERNAL API */
@InternalApi
public final class Unsafe {
  private static final VarHandle stringValueFieldHandle;
  private static final int copyUSAsciiStrToBytesAlgorithm;

  static {
    try {
      VarHandle handle;
      try {
        MethodHandles.Lookup lookup =
            MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
        handle = lookup.findVarHandle(String.class, "value", byte[].class);
      } catch (NoSuchFieldException nsfe) {
        // The platform's implementation of String doesn't have a 'value' field, so we have to use
        // algorithm 0
        handle = null;
      }
      stringValueFieldHandle = handle;

      if (handle != null) {
        // Select optimization algorithm for `copyUSAsciiBytesToStr`.
        // For example algorithm 1 will fail with JDK 11 on ARM32 (Raspberry Pi),
        // and therefore algorithm 0 is selected on that architecture.
        String testStr = "abc";
        if (testUSAsciiStrToBytesAlgorithm1(testStr)) copyUSAsciiStrToBytesAlgorithm = 1;
        else copyUSAsciiStrToBytesAlgorithm = 0;
      } else
        // We know so little about the platform's String implementation that we have
        // no choice but to select algorithm 0
        copyUSAsciiStrToBytesAlgorithm = 0;
    } catch (Throwable t) {
      throw new ExceptionInInitializerError(t);
    }
  }

  static boolean testUSAsciiStrToBytesAlgorithm0(String str) {
    try {
      byte[] bytes = new byte[str.length()];

      // copy of implementation in copyUSAsciiBytesToStr
      byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
      System.arraycopy(strBytes, 0, bytes, 0, str.length());
      // end copy

      String result = copyUSAsciiBytesToStr(str.length(), bytes);
      return str.equals(result);
    } catch (Throwable all) {
      return false;
    }
  }

  static boolean testUSAsciiStrToBytesAlgorithm1(String str) {
    try {
      byte[] bytes = new byte[str.length()];

      // copy of implementation in copyUSAsciiBytesToStr
      final byte[] chars = (byte[]) stringValueFieldHandle.get(str);
      System.arraycopy(chars, 0, bytes, 0, str.length());
      // end copy

      String result = copyUSAsciiBytesToStr(str.length(), bytes);
      return str.equals(result);
    } catch (Throwable all) {
      return false;
    }
  }

  private static String copyUSAsciiBytesToStr(int length, byte[] bytes) {
    char[] resultChars = new char[length];
    int i = 0;
    while (i < length) {
      // UsAscii
      resultChars[i] = (char) bytes[i];
      i += 1;
    }
    return String.valueOf(resultChars, 0, length);
  }

  public static void copyUSAsciiStrToBytes(String str, byte[] bytes) {
    if (copyUSAsciiStrToBytesAlgorithm == 1) {
      final byte[] chars = (byte[]) stringValueFieldHandle.get(str);
      System.arraycopy(chars, 0, bytes, 0, str.length());
    } else {
      byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
      System.arraycopy(strBytes, 0, bytes, 0, str.length());
    }
  }

  public static int fastHash(String str) {
    long s0 = 391408;
    long s1 = 601258;
    int i = 0;

    if (copyUSAsciiStrToBytesAlgorithm == 1) {
      final byte[] chars = (byte[]) stringValueFieldHandle.get(str);
      while (i < str.length()) {
        long x = s0 ^ (long) chars[i++]; // Mix character into PRNG state
        long y = s1;

        // Xorshift128+ round
        s0 = y;
        x ^= x << 23;
        y ^= y >>> 26;
        x ^= x >>> 17;
        s1 = x ^ y;
      }
    } else {
      byte[] chars = str.getBytes(StandardCharsets.US_ASCII);
      while (i < str.length()) {
        long x = s0 ^ (long) chars[i++]; // Mix character into PRNG state
        long y = s1;

        // Xorshift128+ round
        s0 = y;
        x ^= x << 23;
        y ^= y >>> 26;
        x ^= x >>> 17;
        s1 = x ^ y;
      }
    }

    return (int) (s0 + s1);
  }
}
