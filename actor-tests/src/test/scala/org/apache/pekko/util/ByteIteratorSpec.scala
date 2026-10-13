/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2019-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.util

import java.nio.ByteOrder

import org.apache.pekko
import pekko.util.ByteIterator.ByteArrayIterator

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ByteIteratorSpec extends AnyWordSpec with Matchers {
  "A ByteIterator" should {

    "correctly implement indexOf" in {
      // Since the 'indexOf' operator invalidates the iterator,
      // we must create a new one for each test:
      def freshIterator(): ByteIterator = ByteArrayIterator(Array(0x20, 0x20, 0x10, 0x20, 0x20, 0x10))
      freshIterator().indexOf(0x20) should be(0)
      freshIterator().indexOf(0x10) should be(2)

      freshIterator().indexOf(0x20, 1) should be(1)
      freshIterator().indexOf(0x10, 1) should be(2)
      freshIterator().indexOf(0x10, 3) should be(5)

      // There is also an indexOf with another signature, which is hard to invoke :D
      val otherIndexOfHandle =
        java.lang.invoke.MethodHandles.publicLookup().findVirtual(
          classOf[ByteIterator],
          "indexOf",
          java.lang.invoke.MethodType.methodType(classOf[Int], classOf[Byte], classOf[Int]))
      def otherIndexOf(iterator: ByteIterator, byte: Byte, from: Int): Int =
        otherIndexOfHandle.invoke(iterator, byte, from).asInstanceOf[Int]

      otherIndexOf(freshIterator(), 0x20, 1) should be(1)
      otherIndexOf(freshIterator(), 0x10, 1) should be(2)
      otherIndexOf(freshIterator(), 0x10, 3) should be(5)
    }

    "match ByteArrayIterator semantics for getShort/Int/Long across both fast and cross-fragment paths" in {
      // 16 bytes is large enough to fit a long (8B) entirely inside one fragment for the fast
      // path AND to span every possible split for the cross-fragment path.
      val bytes = Array.tabulate[Byte](16)(i => ((i * 31 + 7) & 0xFF).toByte)

      def reference(off: Int, byteOrder: ByteOrder): (Short, Int, Long) = {
        val it = ByteArrayIterator(bytes).drop(off)
        val s = it.clone().getShort(byteOrder)
        val i = it.clone().getInt(byteOrder)
        val l = it.clone().getLong(byteOrder)
        (s, i, l)
      }

      // Build a multi-fragment ByteString for every split point and read at every offset.
      // Splits 1..15 produce two non-empty fragments; the .iterator on the result is a
      // MultiByteArrayIterator. Reads where (off, off+primitiveSize) lies entirely inside
      // a single fragment exercise the fast path; reads that straddle exercise super.
      for (split <- 1 until bytes.length) {
        val left = ByteString.fromArray(bytes, 0, split)
        val right = ByteString.fromArray(bytes, split, bytes.length - split)
        val combined = left ++ right

        for (byteOrder <- Seq(ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN)) {
          implicit val bo: ByteOrder = byteOrder
          for (off <- 0 to bytes.length - java.lang.Long.BYTES) {
            val (refS, refI, refL) = reference(off, byteOrder)

            withClue(s"split=$split, off=$off, byteOrder=$byteOrder, getShort: ") {
              combined.iterator.drop(off).getShort shouldEqual refS
            }
            withClue(s"split=$split, off=$off, byteOrder=$byteOrder, getInt: ") {
              combined.iterator.drop(off).getInt shouldEqual refI
            }
            withClue(s"split=$split, off=$off, byteOrder=$byteOrder, getLong: ") {
              combined.iterator.drop(off).getLong shouldEqual refL
            }
          }
        }
      }
    }
  }
}
