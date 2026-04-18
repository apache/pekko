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

package org.apache.pekko.util

import java.io.{ ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream }
import java.lang.Double.doubleToRawLongBits
import java.lang.Float.floatToRawIntBits
import java.nio.{ ByteBuffer, ByteOrder }
import java.nio.ByteOrder.{ BIG_ENDIAN, LITTLE_ENDIAN }
import java.nio.charset.StandardCharsets

import scala.annotation.nowarn
import scala.collection.mutable.Builder

import org.apache.commons.codec.binary.Hex.encodeHex
import org.scalacheck.{ Arbitrary, Gen }
import org.scalacheck.Arbitrary.arbitrary
import org.scalatestplus.scalacheck.Checkers

import org.apache.pekko
import pekko.io.UnsynchronizedByteArrayInputStream
import pekko.util.ByteString.{ ByteString1, ByteString1C, ByteStrings }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ByteStringSpec extends AnyWordSpec with Matchers with Checkers {

  implicit val betterGeneratorDrivenConfig: PropertyCheckConfiguration =
    PropertyCheckConfiguration().copy(minSuccessful = 1000)

  def genSimpleByteString(min: Int, max: Int) =
    for {
      n <- Gen.choose(min, max)
      b <- Gen.containerOfN[Array, Byte](n, arbitrary[Byte])
      from <- Gen.choose(0, b.length)
      until <- Gen.choose(from, from max b.length)
    } yield ByteString(b).slice(from, until)

  implicit val arbitraryByteString: Arbitrary[ByteString] = Arbitrary {
    Gen.sized { s =>
      for {
        chunks <- Gen.choose(0, s)
        bytes <- Gen.listOfN(chunks, genSimpleByteString(1, 1 max (s / (chunks max 1))))
      } yield bytes.foldLeft(ByteString.empty)(_ ++ _)
    }
  }

  type ByteStringSlice = (ByteString, Int, Int)

  implicit val arbitraryByteStringSlice: Arbitrary[ByteStringSlice] = Arbitrary {
    for {
      xs <- arbitraryByteString.arbitrary
      from <- Gen.choose(0, 0 max (xs.length - 1))
      until <- {
        require(from <= xs.length)
        Gen.choose(from, xs.length)
      }
    } yield (xs, from, until)
  }

  case class ByteStringGrouped(bs: ByteString, size: Int)

  implicit val arbitraryByteStringGrouped: Arbitrary[ByteStringGrouped] = Arbitrary {
    for {
      xs <- arbitraryByteString.arbitrary
      size <- Gen.choose(1, 1 max xs.length)
    } yield ByteStringGrouped(xs, size)
  }

  type ArraySlice[A] = (Array[A], Int, Int)

  def arbSlice[A](arbArray: Arbitrary[Array[A]]): Arbitrary[ArraySlice[A]] = Arbitrary {
    for {
      xs <- arbArray.arbitrary
      from <- Gen.choose(0, xs.length)
      until <- Gen.choose(from, xs.length)
    } yield (xs, from, until)
  }

  def serialize(obj: AnyRef): Array[Byte] = {
    val os = new ByteArrayOutputStream
    val bos = new ObjectOutputStream(os)
    bos.writeObject(obj)
    os.toByteArray
  }

  def deserialize(bytes: Array[Byte]): AnyRef = {
    val is = new ObjectInputStream(new UnsynchronizedByteArrayInputStream(bytes))
    try is.readObject
    finally is.close()
  }

  def testSer(obj: AnyRef) = {
    deserialize(serialize(obj)) == obj
  }

  def hexFromSer(obj: AnyRef) = {
    val os = new ByteArrayOutputStream
    val bos = new ObjectOutputStream(os)
    bos.writeObject(obj)
    String.valueOf(encodeHex(os.toByteArray))
  }

  val arbitraryByteArray: Arbitrary[Array[Byte]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Byte](n, arbitrary[Byte])
    }
  }
  implicit val arbitraryByteArraySlice: Arbitrary[ArraySlice[Byte]] = arbSlice(arbitraryByteArray)
  val arbitraryShortArray: Arbitrary[Array[Short]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Short](n, arbitrary[Short])
    }
  }
  implicit val arbitraryShortArraySlice: Arbitrary[ArraySlice[Short]] = arbSlice(arbitraryShortArray)
  val arbitraryIntArray: Arbitrary[Array[Int]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Int](n, arbitrary[Int])
    }
  }
  implicit val arbitraryIntArraySlice: Arbitrary[ArraySlice[Int]] = arbSlice(arbitraryIntArray)
  val arbitraryLongArray: Arbitrary[Array[Long]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Long](n, arbitrary[Long])
    }
  }
  implicit val arbitraryLongArraySlice: Arbitrary[ArraySlice[Long]] = arbSlice(arbitraryLongArray)
  val arbitraryFloatArray: Arbitrary[Array[Float]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Float](n, arbitrary[Float])
    }
  }
  implicit val arbitraryFloatArraySlice: Arbitrary[ArraySlice[Float]] = arbSlice(arbitraryFloatArray)
  val arbitraryDoubleArray: Arbitrary[Array[Double]] = Arbitrary {
    Gen.sized { n =>
      Gen.containerOfN[Array, Double](n, arbitrary[Double])
    }
  }
  implicit val arbitraryDoubleArraySlice: Arbitrary[ArraySlice[Double]] = arbSlice(arbitraryDoubleArray)

  type ArrayNumBytes[A] = (Array[A], Int)

  implicit val arbitraryLongArrayNumBytes: Arbitrary[ArrayNumBytes[Long]] = Arbitrary {
    for {
      xs <- arbitraryLongArray.arbitrary
      from <- Gen.choose(0, xs.length)
      until <- Gen.choose(from, xs.length)
      bytes <- Gen.choose(0, 8)
    } yield (xs.slice(from, until), bytes)
  }

  implicit val arbitraryByteStringBuilder: Arbitrary[ByteStringBuilder] = Arbitrary(ByteString.newBuilder)

  def likeVector(bs: ByteString)(body: IndexedSeq[Byte] => Any): Boolean = {
    val vec = Vector(bs: _*)
    val a = body(bs)
    val b = body(vec)
    val result = a == b
    if (!result) {
      println(s"$bs => $a != $vec => $b")
    }
    result
  }

  def likeVectors(bsA: ByteString, bsB: ByteString)(body: (IndexedSeq[Byte], IndexedSeq[Byte]) => Any): Boolean = {
    val vecA = Vector(bsA: _*)
    val vecB = Vector(bsB: _*)
    body(bsA, bsB) == body(vecA, vecB)
  }

  @nowarn
  def likeVecIt(bs: ByteString)(body: BufferedIterator[Byte] => Any, strict: Boolean = true): Boolean = {
    val bsIterator = bs.iterator
    val vecIterator = Vector(bs: _*).iterator.buffered
    (body(bsIterator) == body(vecIterator)) &&
    (!strict || (bsIterator.toSeq == vecIterator.toSeq))
  }

  @nowarn
  def likeVecIts(a: ByteString, b: ByteString)(
      body: (BufferedIterator[Byte], BufferedIterator[Byte]) => Any,
      strict: Boolean = true): Boolean = {
    val (bsAIt, bsBIt) = (a.iterator, b.iterator)
    val (vecAIt, vecBIt) = (Vector(a: _*).iterator.buffered, Vector(b: _*).iterator.buffered)
    (body(bsAIt, bsBIt) == body(vecAIt, vecBIt)) &&
    (!strict || (bsAIt.toSeq -> bsBIt.toSeq) == (vecAIt.toSeq -> vecBIt.toSeq))
  }

  def likeVecBld(body: Builder[Byte, _] => Unit): Boolean = {
    val bsBuilder = ByteString.newBuilder
    val vecBuilder = Vector.newBuilder[Byte]

    body(bsBuilder)
    body(vecBuilder)

    bsBuilder.result() == vecBuilder.result()
  }

  def testShortDecoding(slice: ByteStringSlice, byteOrder: ByteOrder): Boolean = {
    val elemSize = 2
    val (bytes, from, until) = slice
    val (n, a, b) = (bytes.length / elemSize, from / elemSize, until / elemSize)
    val reference = new Array[Short](n)
    bytes.asByteBuffer.order(byteOrder).asShortBuffer.get(reference, 0, n)
    val input = bytes.iterator
    val decoded = new Array[Short](n)
    for (i <- 0 until a) decoded(i) = input.getShort(byteOrder)
    input.getShorts(decoded, a, b - a)(byteOrder)
    for (i <- b until n) decoded(i) = input.getShort(byteOrder)
    (decoded.toSeq == reference.toSeq) && (input.toSeq == bytes.drop(n * elemSize))
  }

  def testIntDecoding(slice: ByteStringSlice, byteOrder: ByteOrder): Boolean = {
    val elemSize = 4
    val (bytes, from, until) = slice
    val (n, a, b) = (bytes.length / elemSize, from / elemSize, until / elemSize)
    val reference = new Array[Int](n)
    bytes.asByteBuffer.order(byteOrder).asIntBuffer.get(reference, 0, n)
    val input = bytes.iterator
    val decoded = new Array[Int](n)
    for (i <- 0 until a) decoded(i) = input.getInt(byteOrder)
    input.getInts(decoded, a, b - a)(byteOrder)
    for (i <- b until n) decoded(i) = input.getInt(byteOrder)
    (decoded.toSeq == reference.toSeq) && (input.toSeq == bytes.drop(n * elemSize))
  }

  def testLongDecoding(slice: ByteStringSlice, byteOrder: ByteOrder): Boolean = {
    val elemSize = 8
    val (bytes, from, until) = slice
    val (n, a, b) = (bytes.length / elemSize, from / elemSize, until / elemSize)
    val reference = new Array[Long](n)
    bytes.asByteBuffer.order(byteOrder).asLongBuffer.get(reference, 0, n)
    val input = bytes.iterator
    val decoded = new Array[Long](n)
    for (i <- 0 until a) decoded(i) = input.getLong(byteOrder)
    input.getLongs(decoded, a, b - a)(byteOrder)
    for (i <- b until n) decoded(i) = input.getLong(byteOrder)
    (decoded.toSeq == reference.toSeq) && (input.toSeq == bytes.drop(n * elemSize))
  }

  def testFloatDecoding(slice: ByteStringSlice, byteOrder: ByteOrder): Boolean = {
    val elemSize = 4
    val (bytes, from, until) = slice
    val (n, a, b) = (bytes.length / elemSize, from / elemSize, until / elemSize)
    val reference = new Array[Float](n)
    bytes.asByteBuffer.order(byteOrder).asFloatBuffer.get(reference, 0, n)
    val input = bytes.iterator
    val decoded = new Array[Float](n)
    for (i <- 0 until a) decoded(i) = input.getFloat(byteOrder)
    input.getFloats(decoded, a, b - a)(byteOrder)
    for (i <- b until n) decoded(i) = input.getFloat(byteOrder)
    ((decoded.toSeq.map(floatToRawIntBits)) == (reference.toSeq.map(floatToRawIntBits))) &&
    (input.toSeq == bytes.drop(n * elemSize))
  }

  def testDoubleDecoding(slice: ByteStringSlice, byteOrder: ByteOrder): Boolean = {
    val elemSize = 8
    val (bytes, from, until) = slice
    val (n, a, b) = (bytes.length / elemSize, from / elemSize, until / elemSize)
    val reference = new Array[Double](n)
    bytes.asByteBuffer.order(byteOrder).asDoubleBuffer.get(reference, 0, n)
    val input = bytes.iterator
    val decoded = new Array[Double](n)
    for (i <- 0 until a) decoded(i) = input.getDouble(byteOrder)
    input.getDoubles(decoded, a, b - a)(byteOrder)
    for (i <- b until n) decoded(i) = input.getDouble(byteOrder)
    ((decoded.toSeq.map(doubleToRawLongBits)) == (reference.toSeq.map(doubleToRawLongBits))) &&
    (input.toSeq == bytes.drop(n * elemSize))
  }

  def testShortEncoding(slice: ArraySlice[Short], byteOrder: ByteOrder): Boolean = {
    val elemSize = 2
    val (data, from, to) = slice
    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asShortBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until from) builder.putShort(data(i))(byteOrder)
    builder.putShorts(data, from, to - from)(byteOrder)
    for (i <- to until data.length) builder.putShort(data(i))(byteOrder)
    reference.toSeq == builder.result()
  }

  def testIntEncoding(slice: ArraySlice[Int], byteOrder: ByteOrder): Boolean = {
    val elemSize = 4
    val (data, from, to) = slice
    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asIntBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until from) builder.putInt(data(i))(byteOrder)
    builder.putInts(data, from, to - from)(byteOrder)
    for (i <- to until data.length) builder.putInt(data(i))(byteOrder)
    reference.toSeq == builder.result()
  }

  def testLongEncoding(slice: ArraySlice[Long], byteOrder: ByteOrder): Boolean = {
    val elemSize = 8
    val (data, from, to) = slice
    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asLongBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until from) builder.putLong(data(i))(byteOrder)
    builder.putLongs(data, from, to - from)(byteOrder)
    for (i <- to until data.length) builder.putLong(data(i))(byteOrder)
    reference.toSeq == builder.result()
  }

  def testLongPartEncoding(anb: ArrayNumBytes[Long], byteOrder: ByteOrder): Boolean = {
    val elemSize = 8
    val (data, nBytes) = anb

    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asLongBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until data.length) builder.putLongPart(data(i), nBytes)(byteOrder)

    reference.zipWithIndex
      .collect { // Since there is no partial put on LongBuffer, we need to collect only the interesting bytes
        case (r, i) if byteOrder == ByteOrder.LITTLE_ENDIAN && i % elemSize < nBytes            => r
        case (r, i) if byteOrder == ByteOrder.BIG_ENDIAN && i % elemSize >= (elemSize - nBytes) => r
      }
      .toSeq == builder.result()
  }

  def testFloatEncoding(slice: ArraySlice[Float], byteOrder: ByteOrder): Boolean = {
    val elemSize = 4
    val (data, from, to) = slice
    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asFloatBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until from) builder.putFloat(data(i))(byteOrder)
    builder.putFloats(data, from, to - from)(byteOrder)
    for (i <- to until data.length) builder.putFloat(data(i))(byteOrder)
    reference.toSeq == builder.result()
  }

  def testDoubleEncoding(slice: ArraySlice[Double], byteOrder: ByteOrder): Boolean = {
    val elemSize = 8
    val (data, from, to) = slice
    val reference = new Array[Byte](data.length * elemSize)
    ByteBuffer.wrap(reference).order(byteOrder).asDoubleBuffer.put(data)
    val builder = ByteString.newBuilder
    for (i <- 0 until from) builder.putDouble(data(i))(byteOrder)
    builder.putDoubles(data, from, to - from)(byteOrder)
    for (i <- to until data.length) builder.putDouble(data(i))(byteOrder)
    reference.toSeq == builder.result()
  }

  "ByteString1" must {
    "drop" in {
      ByteString1.empty.drop(-1) should ===(ByteString(""))
      ByteString1.empty.drop(0) should ===(ByteString(""))
      ByteString1.empty.drop(1) should ===(ByteString(""))
      ByteString1.fromString("a").drop(-1) should ===(ByteString("a"))
      ByteString1.fromString("a").drop(0) should ===(ByteString("a"))
      ByteString1.fromString("a").drop(1) should ===(ByteString(""))
      ByteString1.fromString("a").drop(2) should ===(ByteString(""))
      ByteString1.fromString("abc").drop(-1) should ===(ByteString("abc"))
      ByteString1.fromString("abc").drop(0) should ===(ByteString("abc"))
      ByteString1.fromString("abc").drop(1) should ===(ByteString("bc"))
      ByteString1.fromString("abc").drop(2) should ===(ByteString("c"))
      ByteString1.fromString("abc").drop(3) should ===(ByteString(""))
      ByteString1.fromString("abc").drop(4) should ===(ByteString(""))
      ByteString1.fromString("0123456789").drop(1).take(2) should ===(ByteString("12"))
      ByteString1.fromString("0123456789").drop(5).take(4).drop(1).take(2) should ===(ByteString("67"))
    }
    "dropRight" in {
      ByteString1.empty.dropRight(-1) should ===(ByteString(""))
      ByteString1.empty.dropRight(0) should ===(ByteString(""))
      ByteString1.empty.dropRight(1) should ===(ByteString(""))
      ByteString1.fromString("a").dropRight(-1) should ===(ByteString("a"))
      ByteString1.fromString("a").dropRight(0) should ===(ByteString("a"))
      ByteString1.fromString("a").dropRight(1) should ===(ByteString(""))
      ByteString1.fromString("a").dropRight(2) should ===(ByteString(""))
      ByteString1.fromString("abc").dropRight(-1) should ===(ByteString("abc"))
      ByteString1.fromString("abc").dropRight(0) should ===(ByteString("abc"))
      ByteString1.fromString("abc").dropRight(1) should ===(ByteString("ab"))
      ByteString1.fromString("abc").dropRight(2) should ===(ByteString("a"))
      ByteString1.fromString("abc").dropRight(3) should ===(ByteString(""))
      ByteString1.fromString("abc").dropRight(4) should ===(ByteString(""))
      ByteString1.fromString("0123456789").dropRight(1).take(2) should ===(ByteString("01"))
      ByteString1.fromString("0123456789").dropRight(5).take(4).drop(1).take(2) should ===(ByteString("12"))
    }
    "take" in {
      ByteString1.empty.take(-1) should ===(ByteString(""))
      ByteString1.empty.take(0) should ===(ByteString(""))
      ByteString1.empty.take(1) should ===(ByteString(""))
      ByteString1.fromString("a").take(1) should ===(ByteString("a"))
      ByteString1.fromString("ab").take(-1) should ===(ByteString(""))
      ByteString1.fromString("ab").take(0) should ===(ByteString(""))
      ByteString1.fromString("ab").take(1) should ===(ByteString("a"))
      ByteString1.fromString("ab").take(2) should ===(ByteString("ab"))
      ByteString1.fromString("ab").take(3) should ===(ByteString("ab"))
      ByteString1.fromString("0123456789").take(3).drop(1) should ===(ByteString("12"))
      ByteString1.fromString("0123456789").take(10).take(8).drop(3).take(5) should ===(ByteString("34567"))
    }
    "copyToArray" in {
      val byteString = ByteString1(Array[Byte](1, 2, 3, 4, 5), startIndex = 1, length = 3)
      def verify(f: Array[Byte] => Unit)(expected: Byte*): Unit = {
        val array = Array.fill[Byte](3)(0)
        f(array)
        array should ===(expected.toArray)
      }

      verify(byteString.copyToArray(_, 0, 1))(2, 0, 0)
      verify(byteString.copyToArray(_, 1, 1))(0, 2, 0)
      verify(byteString.copyToArray(_, 2, 1))(0, 0, 2)
      verify(byteString.copyToArray(_, 3, 1))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 2))(2, 3, 0)
      verify(byteString.copyToArray(_, 1, 2))(0, 2, 3)
      verify(byteString.copyToArray(_, 2, 2))(0, 0, 2)
      verify(byteString.copyToArray(_, 3, 2))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 3))(2, 3, 4)
      verify(byteString.copyToArray(_, 1, 3))(0, 2, 3)
      verify(byteString.copyToArray(_, 2, 3))(0, 0, 2)
      verify(byteString.copyToArray(_, 3, 3))(0, 0, 0)
    }
  }
  "ByteString1C" must {
    "drop" in {
      ByteString1C.fromString("").drop(-1) should ===(ByteString(""))
      ByteString1C.fromString("").drop(0) should ===(ByteString(""))
      ByteString1C.fromString("").drop(1) should ===(ByteString(""))
      ByteString1C.fromString("a").drop(-1) should ===(ByteString("a"))
      ByteString1C.fromString("a").drop(0) should ===(ByteString("a"))
      ByteString1C.fromString("a").drop(1) should ===(ByteString(""))
      ByteString1C.fromString("a").drop(2) should ===(ByteString(""))
      ByteString1C.fromString("abc").drop(-1) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").drop(0) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").drop(1) should ===(ByteString("bc"))
      ByteString1C.fromString("abc").drop(2) should ===(ByteString("c"))
      ByteString1C.fromString("abc").drop(3) should ===(ByteString(""))
      ByteString1C.fromString("abc").drop(4) should ===(ByteString(""))
      ByteString1C.fromString("0123456789").drop(1).take(2) should ===(ByteString("12"))
      ByteString1C.fromString("0123456789").drop(5).take(4).drop(1).take(2) should ===(ByteString("67"))
    }
    "dropRight" in {
      ByteString1C.fromString("").dropRight(-1) should ===(ByteString(""))
      ByteString1C.fromString("").dropRight(0) should ===(ByteString(""))
      ByteString1C.fromString("").dropRight(1) should ===(ByteString(""))
      ByteString1C.fromString("a").dropRight(-1) should ===(ByteString("a"))
      ByteString1C.fromString("a").dropRight(0) should ===(ByteString("a"))
      ByteString1C.fromString("a").dropRight(1) should ===(ByteString(""))
      ByteString1C.fromString("a").dropRight(2) should ===(ByteString(""))
      ByteString1C.fromString("abc").dropRight(-1) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").dropRight(0) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").dropRight(1) should ===(ByteString("ab"))
      ByteString1C.fromString("abc").dropRight(2) should ===(ByteString("a"))
      ByteString1C.fromString("abc").dropRight(3) should ===(ByteString(""))
      ByteString1C.fromString("abc").dropRight(4) should ===(ByteString(""))
      ByteString1C.fromString("0123456789").dropRight(1).take(2) should ===(ByteString("01"))
      ByteString1C.fromString("0123456789").dropRight(5).take(4).drop(1).take(2) should ===(ByteString("12"))
    }
    "take" in {
      ByteString1.fromString("abcdefg").drop(1).take(0) should ===(ByteString(""))
      ByteString1.fromString("abcdefg").drop(1).take(-1) should ===(ByteString(""))
      ByteString1.fromString("abcdefg").drop(1).take(-2) should ===(ByteString(""))
      ByteString1.fromString("abcdefg").drop(2) should ===(ByteString("cdefg"))
      ByteString1.fromString("abcdefg").drop(2).take(1) should ===(ByteString("c"))
    }
    "copyToArray" in {
      val byteString = ByteString1C(Array[Byte](1, 2, 3))
      def verify(f: Array[Byte] => Unit)(expected: Byte*): Unit = {
        val array = Array.fill[Byte](3)(0)
        f(array)
        array should ===(expected.toArray)
      }

      verify(byteString.copyToArray(_, 0, 1))(1, 0, 0)
      verify(byteString.copyToArray(_, 1, 1))(0, 1, 0)
      verify(byteString.copyToArray(_, 2, 1))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 1))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 2))(1, 2, 0)
      verify(byteString.copyToArray(_, 1, 2))(0, 1, 2)
      verify(byteString.copyToArray(_, 2, 2))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 2))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 3))(1, 2, 3)
      verify(byteString.copyToArray(_, 1, 3))(0, 1, 2)
      verify(byteString.copyToArray(_, 2, 3))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 3))(0, 0, 0)
    }
  }
  "ByteStrings" must {
    "drop" in {
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).drop(Int.MinValue) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).drop(-1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).drop(0) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).drop(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).drop(Int.MaxValue) should ===(ByteString(""))

      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(Int.MinValue) should ===(
        ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(-1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(0) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).drop(Int.MaxValue) should ===(ByteString(""))

      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(Int.MinValue) should ===(
        ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(-1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(0) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).drop(Int.MaxValue) should ===(ByteString(""))

      val bss =
        ByteStrings(Vector(ByteString1.fromString("a"), ByteString1.fromString("bc"), ByteString1.fromString("def")))

      bss.drop(Int.MinValue) should ===(ByteString("abcdef"))
      bss.drop(-1) should ===(ByteString("abcdef"))
      bss.drop(0) should ===(ByteString("abcdef"))
      bss.drop(1) should ===(ByteString("bcdef"))
      bss.drop(2) should ===(ByteString("cdef"))
      bss.drop(3) should ===(ByteString("def"))
      bss.drop(4) should ===(ByteString("ef"))
      bss.drop(5) should ===(ByteString("f"))
      bss.drop(6) should ===(ByteString(""))
      bss.drop(7) should ===(ByteString(""))
      bss.drop(Int.MaxValue) should ===(ByteString(""))

      ByteString("0123456789").drop(5).take(2) should ===(ByteString("56"))
      ByteString("0123456789").drop(5).drop(3).take(1) should ===(ByteString("8"))
      (ByteString1C.fromString("a") ++ ByteString1.fromString("bc")).drop(2) should ===(ByteString("c"))
    }
    "dropRight" in {
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).dropRight(Int.MinValue) should ===(
        ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).dropRight(-1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).dropRight(0) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).dropRight(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("")).dropRight(Int.MaxValue) should ===(
        ByteString(""))

      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(Int.MinValue) should ===(
        ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(-1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(0) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).dropRight(Int.MaxValue) should ===(
        ByteString(""))

      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(Int.MinValue) should ===(
        ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(-1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(0) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).dropRight(Int.MaxValue) should ===(
        ByteString(""))

      val bss =
        ByteStrings(Vector(ByteString1.fromString("a"), ByteString1.fromString("bc"), ByteString1.fromString("def")))

      bss.dropRight(Int.MinValue) should ===(ByteString("abcdef"))
      bss.dropRight(-1) should ===(ByteString("abcdef"))
      bss.dropRight(0) should ===(ByteString("abcdef"))
      bss.dropRight(1) should ===(ByteString("abcde"))
      bss.dropRight(2) should ===(ByteString("abcd"))
      bss.dropRight(3) should ===(ByteString("abc"))
      bss.dropRight(4) should ===(ByteString("ab"))
      bss.dropRight(5) should ===(ByteString("a"))
      bss.dropRight(6) should ===(ByteString(""))
      bss.dropRight(7) should ===(ByteString(""))
      bss.dropRight(Int.MaxValue) should ===(ByteString(""))

      ByteString("0123456789").dropRight(5).take(2) should ===(ByteString("01"))
      ByteString("0123456789").dropRight(5).drop(3).take(1) should ===(ByteString("3"))
      (ByteString1C.fromString("a") ++ ByteString1.fromString("bc")).dropRight(2) should ===(ByteString("a"))
    }
    "slice" in {
      ByteStrings(ByteString1.fromString(""), ByteString1.fromString("a")).slice(1, 1) should ===(ByteString(""))
      // We explicitly test all edge cases to always test them, refs bug #21237
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-10, 10) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-10, 0) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-10, 1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(0, 1) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(0, 10) should ===(ByteString("a"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(1, 10) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(1, -2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-10, -100) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-100, -10) should ===(ByteString(""))
      // Get an empty if `from` is greater then `until`
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(1, 0) should ===(ByteString(""))

      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(2, 2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(2, 3) should ===(ByteString("c"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(2, 4) should ===(ByteString("cd"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(3, 4) should ===(ByteString("d"))
      // Can obtain expected results from 6 basic patterns
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(-10, 10) should ===(
        ByteString("abcd"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(-10, 0) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(-10, 4) should ===(
        ByteString("abcd"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(0, 4) should ===(ByteString("abcd"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(1, -2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(0, 10) should ===(
        ByteString("abcd"))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(-10, -100) should ===(
        ByteString(""))
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(-100, -10) should ===(
        ByteString(""))

      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(1, -2) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-10, -100) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("")).slice(-100, -10) should ===(ByteString(""))

      // various edge cases using raw ByteString1
      ByteString1.fromString("cd").slice(100, 10) should ===(ByteString(""))
      ByteString1.fromString("cd").slice(100, 1000) should ===(ByteString(""))
      ByteString1.fromString("cd").slice(-10, -5) should ===(ByteString(""))
      ByteString1.fromString("cd").slice(-2, -5) should ===(ByteString(""))
      ByteString1.fromString("cd").slice(-2, 1) should ===(ByteString("c"))
      ByteString1.fromString("abcd").slice(1, -1) should ===(ByteString(""))

      // Get an empty if `from` is greater than `until`
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd")).slice(4, 0) should ===(ByteString(""))
    }
    "take" in {
      ByteString.empty.take(-1) should ===(ByteString(""))
      ByteString.empty.take(0) should ===(ByteString(""))
      ByteString.empty.take(1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).drop(1).take(0) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).drop(1).take(-1) should ===(ByteString(""))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).drop(1).take(-2) should ===(ByteString(""))
      (ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")) ++ ByteString1.fromString("defg"))
        .drop(2) should ===(ByteString("cdefg"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).drop(2).take(1) should ===(ByteString("c"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).take(100) should ===(ByteString("abc"))
      ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("bc")).drop(1).take(100) should ===(
        ByteString("bc"))
    }
    "indexOf" in {
      ByteString.empty.indexOf(5) should ===(-1)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.indexOf('a') should ===(0)
      byteString1.indexOf('b') should ===(1)
      byteString1.indexOf('c') should ===(2)
      byteString1.indexOf('d') should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("efg"))
      byteStrings.indexOf('a') should ===(0)
      byteStrings.indexOf('c') should ===(2)
      byteStrings.indexOf('d') should ===(-1)
      byteStrings.indexOf('e') should ===(3)
      byteStrings.indexOf('f') should ===(4)
      byteStrings.indexOf('g') should ===(5)

      val compact = byteStrings.compact
      compact.indexOf('a') should ===(0)
      compact.indexOf('c') should ===(2)
      compact.indexOf('d') should ===(-1)
      compact.indexOf('e') should ===(3)
      compact.indexOf('f') should ===(4)
      compact.indexOf('g') should ===(5)

      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      byteStringLong.indexOf('m') should ===(12)
      byteStringLong.indexOf('z') should ===(25)
      byteStringLong.indexOf('a') should ===(0)
    }
    "lastIndexOf" in {
      ByteString.empty.indexOf(5) should ===(-1)
      val byteString1 = ByteString1.fromString("abb")
      byteString1.lastIndexOf('a') should ===(0)
      byteString1.lastIndexOf('b') should ===(2)
      byteString1.lastIndexOf('d') should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abb"), ByteString1.fromString("efg"))
      byteStrings.lastIndexOf('a') should ===(0)
      byteStrings.lastIndexOf('b') should ===(2)
      byteStrings.lastIndexOf('d') should ===(-1)
      byteStrings.lastIndexOf('e') should ===(3)
      byteStrings.lastIndexOf('f') should ===(4)
      byteStrings.lastIndexOf('g') should ===(5)

      val compact = byteStrings.compact
      compact.lastIndexOf('a') should ===(0)
      compact.lastIndexOf('b') should ===(2)
      compact.lastIndexOf('d') should ===(-1)
      compact.lastIndexOf('e') should ===(3)
      compact.lastIndexOf('f') should ===(4)
      compact.lastIndexOf('g') should ===(5)

      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      byteStringLong.lastIndexOf('m') should ===(12)
      byteStringLong.lastIndexOf('z') should ===(25)
      byteStringLong.lastIndexOf('a') should ===(0)
    }
    "indexOf from offset" in {
      ByteString.empty.indexOf(5, -1) should ===(-1)
      ByteString.empty.indexOf(5, 0) should ===(-1)
      ByteString.empty.indexOf(5, 1) should ===(-1)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.indexOf('d', -1) should ===(-1)
      byteString1.indexOf('d', 0) should ===(-1)
      byteString1.indexOf('d', 1) should ===(-1)
      byteString1.indexOf('d', 4) should ===(-1)
      byteString1.indexOf('a', -1) should ===(0)
      byteString1.indexOf('a', 0) should ===(0)
      byteString1.indexOf('a', 1) should ===(-1)

      val array = Array[Byte]('x', 'y', 'z', 'a', 'b', 'c')
      val byteString2 = ByteString1(array, 3, 3)
      byteString2.indexOf('x', -1) should ===(-1)
      byteString2.indexOf('x', 0) should ===(-1)
      byteString2.indexOf('x', 1) should ===(-1)
      byteString2.indexOf('x', 4) should ===(-1)
      byteString2.indexOf('a', -1) should ===(0)
      byteString2.indexOf('a', 0) should ===(0)
      byteString2.indexOf('a', 1) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("efg"))
      byteStrings.indexOf('c', -1) should ===(2)
      byteStrings.indexOf('c', 0) should ===(2)
      byteStrings.indexOf('c', 2) should ===(2)
      byteStrings.indexOf('c', 3) should ===(-1)

      byteStrings.indexOf('e', -1) should ===(3)
      byteStrings.indexOf('e', 0) should ===(3)
      byteStrings.indexOf('e', 1) should ===(3)
      byteStrings.indexOf('e', 4) should ===(-1)
      byteStrings.indexOf('e', 6) should ===(-1)

      byteStrings.indexOf('g', -1) should ===(5)
      byteStrings.indexOf('g', 0) should ===(5)
      byteStrings.indexOf('g', 1) should ===(5)
      byteStrings.indexOf('g', 4) should ===(5)
      byteStrings.indexOf('g', 5) should ===(5)
      byteStrings.indexOf('g', 6) should ===(-1)

      val compact = byteStrings.compact
      compact.indexOf('c', -1) should ===(2)
      compact.indexOf('c', 0) should ===(2)
      compact.indexOf('c', 2) should ===(2)
      compact.indexOf('c', 3) should ===(-1)

      compact.indexOf('e', -1) should ===(3)
      compact.indexOf('e', 0) should ===(3)
      compact.indexOf('e', 1) should ===(3)
      compact.indexOf('e', 4) should ===(-1)
      compact.indexOf('e', 6) should ===(-1)

      compact.indexOf('g', -1) should ===(5)
      compact.indexOf('g', 0) should ===(5)
      compact.indexOf('g', 1) should ===(5)
      compact.indexOf('g', 4) should ===(5)
      compact.indexOf('g', 5) should ===(5)
      compact.indexOf('g', 6) should ===(-1)

      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      byteStringLong.indexOf('m', 2) should ===(12)
      byteStringLong.indexOf('z', 2) should ===(25)
      byteStringLong.indexOf('a', 2) should ===(-1)
    }
    "lastIndexOf from offset" in {
      ByteString.empty.lastIndexOf(5, -1) should ===(-1)
      ByteString.empty.lastIndexOf(5, 0) should ===(-1)
      ByteString.empty.lastIndexOf(5, 1) should ===(-1)
      val byteString1 = ByteString1.fromString("abb")
      byteString1.lastIndexOf('d', -1) should ===(-1)
      byteString1.lastIndexOf('d', 4) should ===(-1)
      byteString1.lastIndexOf('d', 1) should ===(-1)
      byteString1.lastIndexOf('d', 0) should ===(-1)
      byteString1.lastIndexOf('a', -1) should ===(-1)
      byteString1.lastIndexOf('a', 0) should ===(0)
      byteString1.lastIndexOf('a', 1) should ===(0)
      byteString1.lastIndexOf('b', 2) should ===(2)
      byteString1.lastIndexOf('b', 1) should ===(1)
      byteString1.lastIndexOf('b', 0) should ===(-1)

      val array = Array[Byte]('x', 'y', 'z', 'a', 'b', 'b')
      val byteString2 = ByteString1(array, 3, 3)
      byteString2.lastIndexOf('x', -1) should ===(-1)
      byteString2.lastIndexOf('x', 0) should ===(-1)
      byteString2.lastIndexOf('x', 3) should ===(-1)
      byteString2.lastIndexOf('x', 4) should ===(-1)
      byteString2.lastIndexOf('a', -1) should ===(-1)
      byteString2.lastIndexOf('a', 0) should ===(0)
      byteString2.lastIndexOf('a', 1) should ===(0)
      byteString2.lastIndexOf('b', 2) should ===(2)
      byteString2.lastIndexOf('b', 1) should ===(1)
      byteString2.lastIndexOf('b', 0) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abb"), ByteString1.fromString("efg"))
      byteStrings.lastIndexOf('e', 6) should ===(3)
      byteStrings.lastIndexOf('e', 4) should ===(3)
      byteStrings.lastIndexOf('e', 1) should ===(-1)
      byteStrings.lastIndexOf('e', 0) should ===(-1)
      byteStrings.lastIndexOf('e', -1) should ===(-1)

      byteStrings.lastIndexOf('b', 6) should ===(2)
      byteStrings.lastIndexOf('b', 4) should ===(2)
      byteStrings.lastIndexOf('b', 1) should ===(1)
      byteStrings.lastIndexOf('b', 0) should ===(-1)
      byteStrings.lastIndexOf('b', -1) should ===(-1)

      val compact = byteStrings.compact
      compact.lastIndexOf('e', 6) should ===(3)
      compact.lastIndexOf('e', 4) should ===(3)
      compact.lastIndexOf('e', 1) should ===(-1)
      compact.lastIndexOf('e', 0) should ===(-1)
      compact.lastIndexOf('e', -1) should ===(-1)

      compact.lastIndexOf('b', 6) should ===(2)
      compact.lastIndexOf('b', 4) should ===(2)
      compact.lastIndexOf('b', 1) should ===(1)
      compact.lastIndexOf('b', 0) should ===(-1)
      compact.lastIndexOf('b', -1) should ===(-1)

      val concat0 = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("dd"))
      concat0.lastIndexOf('d'.toByte, 2) should ===(2)
      concat0.lastIndexOf('d'.toByte, 3) should ===(3)

      // end larger than length - 1 should be clamped to the last valid index
      val bs5 = ByteString1.fromString("abcde")
      bs5.lastIndexOf('e', 100) should ===(4)
      bs5.lastIndexOf('a', 100) should ===(0)
      bs5.lastIndexOf('z', 100) should ===(-1)
    }
    "lastIndexOf (specialized)" in {
      ByteString.empty.lastIndexOf(5.toByte, -1) should ===(-1)
      ByteString.empty.lastIndexOf(5.toByte, 0) should ===(-1)
      ByteString.empty.lastIndexOf(5.toByte, 1) should ===(-1)
      ByteString.empty.lastIndexOf(5.toByte) should ===(-1)
      val byteString1 = ByteString1.fromString("abb")
      byteString1.lastIndexOf('d'.toByte) should ===(-1)
      byteString1.lastIndexOf('d'.toByte, -1) should ===(-1)
      byteString1.lastIndexOf('d'.toByte, 4) should ===(-1)
      byteString1.lastIndexOf('d'.toByte, 1) should ===(-1)
      byteString1.lastIndexOf('d'.toByte, 0) should ===(-1)
      byteString1.lastIndexOf('a'.toByte, -1) should ===(-1)
      byteString1.lastIndexOf('a'.toByte) should ===(0)
      byteString1.lastIndexOf('a'.toByte, 0) should ===(0)
      byteString1.lastIndexOf('a'.toByte, 1) should ===(0)
      byteString1.lastIndexOf('b'.toByte) should ===(2)
      byteString1.lastIndexOf('b'.toByte, 2) should ===(2)
      byteString1.lastIndexOf('b'.toByte, 1) should ===(1)
      byteString1.lastIndexOf('b'.toByte, 0) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abb"), ByteString1.fromString("efg"))
      byteStrings.lastIndexOf('e'.toByte) should ===(3)
      byteStrings.lastIndexOf('e'.toByte, 6) should ===(3)
      byteStrings.lastIndexOf('e'.toByte, 4) should ===(3)
      byteStrings.lastIndexOf('e'.toByte, 1) should ===(-1)
      byteStrings.lastIndexOf('e'.toByte, 0) should ===(-1)
      byteStrings.lastIndexOf('e'.toByte, -1) should ===(-1)

      byteStrings.lastIndexOf('b'.toByte) should ===(2)
      byteStrings.lastIndexOf('b'.toByte, 6) should ===(2)
      byteStrings.lastIndexOf('b'.toByte, 4) should ===(2)
      byteStrings.lastIndexOf('b'.toByte, 1) should ===(1)
      byteStrings.lastIndexOf('b'.toByte, 0) should ===(-1)
      byteStrings.lastIndexOf('b'.toByte, -1) should ===(-1)

      val compact = byteStrings.compact
      compact.lastIndexOf('e'.toByte) should ===(3)
      compact.lastIndexOf('e'.toByte, 6) should ===(3)
      compact.lastIndexOf('e'.toByte, 4) should ===(3)
      compact.lastIndexOf('e'.toByte, 1) should ===(-1)
      compact.lastIndexOf('e'.toByte, 0) should ===(-1)
      compact.lastIndexOf('e'.toByte, -1) should ===(-1)

      compact.lastIndexOf('b'.toByte) should ===(2)
      compact.lastIndexOf('b'.toByte, 6) should ===(2)
      compact.lastIndexOf('b'.toByte, 4) should ===(2)
      compact.lastIndexOf('b'.toByte, 1) should ===(1)
      compact.lastIndexOf('b'.toByte, 0) should ===(-1)
      compact.lastIndexOf('b'.toByte, -1) should ===(-1)

      val sliced = ByteString1.fromString("xxabcdefghijk").drop(2)
      sliced.lastIndexOf('k'.toByte) should ===(10)

      val zeros = ByteString(Array[Byte](0, 1, 0, 1))
      zeros.lastIndexOf(0.toByte) should ===(2)
      val neg = ByteString(Array[Byte](-1, 0, -1))
      neg.lastIndexOf((-1).toByte) should ===(2)

      val concat0 = makeMultiByteStringsSample()
      concat0.lastIndexOf(0xFF.toByte) should ===(18)
      concat0.lastIndexOf(0xFF.toByte, 18) should ===(18)
      concat0.lastIndexOf(0xFF.toByte, 17) should ===(0)
      concat0.lastIndexOf(0xFE.toByte) should ===(-1)

      // Single-byte ByteString
      val single = ByteString1(Array[Byte]('x'))
      single.lastIndexOf('x'.toByte) should ===(0)
      single.lastIndexOf('y'.toByte) should ===(-1)

      // SWAR boundary: exactly 7 bytes (only tail, no full chunk)
      val len7 = ByteString1.fromString("abcdefg")
      len7.lastIndexOf('g'.toByte) should ===(6)
      len7.lastIndexOf('a'.toByte) should ===(0)
      len7.lastIndexOf('d'.toByte) should ===(3)
      len7.lastIndexOf('z'.toByte) should ===(-1)

      // SWAR boundary: exactly 8 bytes (one full chunk, no tail)
      val len8 = ByteString1.fromString("abcdefgh")
      len8.lastIndexOf('h'.toByte) should ===(7)
      len8.lastIndexOf('a'.toByte) should ===(0)
      len8.lastIndexOf('d'.toByte) should ===(3)
      len8.lastIndexOf('z'.toByte) should ===(-1)

      // SWAR boundary: exactly 9 bytes (1-byte tail + 1 full chunk)
      val len9 = ByteString1.fromString("abcdefghi")
      len9.lastIndexOf('i'.toByte) should ===(8) // found in tail
      len9.lastIndexOf('a'.toByte) should ===(0) // found in chunk
      len9.lastIndexOf('h'.toByte) should ===(7) // last byte of chunk
      len9.lastIndexOf('z'.toByte) should ===(-1)

      // SWAR boundary: exactly 16 bytes (2 full chunks, no tail)
      val len16 = ByteString1.fromString("abcdefghijklmnop")
      len16.lastIndexOf('p'.toByte) should ===(15) // last byte of rightmost chunk
      len16.lastIndexOf('a'.toByte) should ===(0) // first byte of leftmost chunk
      len16.lastIndexOf('h'.toByte) should ===(7) // last byte of leftmost chunk
      len16.lastIndexOf('i'.toByte) should ===(8) // first byte of rightmost chunk

      // All-same-byte array longer than 8: last index is the highest
      val allSame = ByteString(Array[Byte](7, 7, 7, 7, 7, 7, 7, 7, 7)) // 9 bytes
      allSame.lastIndexOf(7.toByte) should ===(8)
      allSame.lastIndexOf(7.toByte, 5) should ===(5)
      allSame.lastIndexOf(7.toByte, 0) should ===(0)

      // ByteString1 with startIndex offset: find a byte residing in the SWAR chunk
      val slicedLong = ByteString1.fromString("xxabcdefghijk").drop(2) // "abcdefghijk", 11 bytes
      slicedLong.lastIndexOf('a'.toByte) should ===(0) // first byte, found via chunk scan
      slicedLong.lastIndexOf('h'.toByte) should ===(7) // last byte of chunk

      val long1 = ByteString1.fromString("abcdefghijklmnop") // 16 bytes
      long1.lastIndexOf('a'.toByte) should ===(0)
      long1.lastIndexOf('p'.toByte) should ===(15)
      long1.lastIndexOf('h'.toByte, 7) should ===(7)
      long1.lastIndexOf('h'.toByte, 6) should ===(-1)

      val concat1 = makeMultiByteStringsWithEmptyComponents()
      concat1.lastIndexOf(16.toByte) should ===(17)
    }
    "indexOf (specialized)" in {
      ByteString.empty.indexOf(5.toByte) should ===(-1)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.indexOf('a'.toByte) should ===(0)
      byteString1.indexOf('b'.toByte) should ===(1)
      byteString1.indexOf('c'.toByte) should ===(2)
      byteString1.indexOf('d'.toByte) should ===(-1)

      val array = Array[Byte]('x', 'y', 'z', 'a', 'b', 'c')
      val byteString2 = ByteString1(array, 3, 2)
      byteString2.indexOf('x'.toByte) should ===(-1)
      byteString2.indexOf('a'.toByte) should ===(0)
      byteString2.indexOf('b'.toByte) should ===(1)
      byteString2.indexOf('c'.toByte) should ===(-1)
      byteString2.indexOf('d'.toByte) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("efg"))
      byteStrings.indexOf('a'.toByte) should ===(0)
      byteStrings.indexOf('c'.toByte) should ===(2)
      byteStrings.indexOf('d'.toByte) should ===(-1)
      byteStrings.indexOf('e'.toByte) should ===(3)
      byteStrings.indexOf('f'.toByte) should ===(4)
      byteStrings.indexOf('g'.toByte) should ===(5)

      val compact = byteStrings.compact
      compact.indexOf('a'.toByte) should ===(0)
      compact.indexOf('c'.toByte) should ===(2)
      compact.indexOf('d'.toByte) should ===(-1)
      compact.indexOf('e'.toByte) should ===(3)
      compact.indexOf('f'.toByte) should ===(4)
      compact.indexOf('g'.toByte) should ===(5)

      val concat0 = makeMultiByteStringsSample()
      concat0.indexOf(0xFF.toByte) should ===(0)
      concat0.indexOf(16.toByte) should ===(17)
      concat0.indexOf(0xFE.toByte) should ===(-1)

      val concat1 = makeMultiByteStringsWithEmptyComponents()
      concat1.indexOf(0xFF.toByte) should ===(0)
      concat1.indexOf(16.toByte) should ===(17)
      concat1.indexOf(0xFE.toByte) should ===(-1)
    }
    "indexOf (specialized) from offset" in {
      ByteString.empty.indexOf(5.toByte, -1) should ===(-1)
      ByteString.empty.indexOf(5.toByte, 0) should ===(-1)
      ByteString.empty.indexOf(5.toByte, 1) should ===(-1)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.indexOf('d'.toByte, -1) should ===(-1)
      byteString1.indexOf('d'.toByte, 0) should ===(-1)
      byteString1.indexOf('d'.toByte, 1) should ===(-1)
      byteString1.indexOf('d'.toByte, 4) should ===(-1)
      byteString1.indexOf('a'.toByte, -1) should ===(0)
      byteString1.indexOf('a'.toByte, 0) should ===(0)
      byteString1.indexOf('a'.toByte, 1) should ===(-1)

      val array = Array[Byte]('x', 'y', 'z', 'a', 'b', 'c')
      val byteString2 = ByteString1(array, 3, 2)
      byteString2.indexOf('x'.toByte, -1) should ===(-1)
      byteString2.indexOf('x'.toByte, 0) should ===(-1)
      byteString2.indexOf('x'.toByte, 1) should ===(-1)
      byteString2.indexOf('x'.toByte, 4) should ===(-1)
      byteString2.indexOf('a'.toByte, -1) should ===(0)
      byteString2.indexOf('a'.toByte, 0) should ===(0)
      byteString2.indexOf('a'.toByte, 1) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("efg"))
      byteStrings.indexOf('c'.toByte, -1) should ===(2)
      byteStrings.indexOf('c'.toByte, 0) should ===(2)
      byteStrings.indexOf('c'.toByte, 2) should ===(2)
      byteStrings.indexOf('c'.toByte, 3) should ===(-1)

      byteStrings.indexOf('e'.toByte, -1) should ===(3)
      byteStrings.indexOf('e'.toByte, 0) should ===(3)
      byteStrings.indexOf('e'.toByte, 1) should ===(3)
      byteStrings.indexOf('e'.toByte, 4) should ===(-1)
      byteStrings.indexOf('e'.toByte, 6) should ===(-1)

      byteStrings.indexOf('g'.toByte, -1) should ===(5)
      byteStrings.indexOf('g'.toByte, 0) should ===(5)
      byteStrings.indexOf('g'.toByte, 1) should ===(5)
      byteStrings.indexOf('g'.toByte, 4) should ===(5)
      byteStrings.indexOf('g'.toByte, 5) should ===(5)
      byteStrings.indexOf('g'.toByte, 6) should ===(-1)

      val compact = byteStrings.compact
      compact.indexOf('c'.toByte, -1) should ===(2)
      compact.indexOf('c'.toByte, 0) should ===(2)
      compact.indexOf('c'.toByte, 2) should ===(2)
      compact.indexOf('c'.toByte, 3) should ===(-1)

      compact.indexOf('e'.toByte, -1) should ===(3)
      compact.indexOf('e'.toByte, 0) should ===(3)
      compact.indexOf('e'.toByte, 1) should ===(3)
      compact.indexOf('e'.toByte, 4) should ===(-1)
      compact.indexOf('e'.toByte, 6) should ===(-1)

      compact.indexOf('g'.toByte, -1) should ===(5)
      compact.indexOf('g'.toByte, 0) should ===(5)
      compact.indexOf('g'.toByte, 1) should ===(5)
      compact.indexOf('g'.toByte, 4) should ===(5)
      compact.indexOf('g'.toByte, 5) should ===(5)
      compact.indexOf('g'.toByte, 6) should ===(-1)

      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      byteStringLong.indexOf('m', 2) should ===(12)
      byteStringLong.indexOf('z', 2) should ===(25)
      byteStringLong.indexOf('a', 2) should ===(-1)

      val concat0 = makeMultiByteStringsSample()
      concat0.indexOf(0xFF.toByte, 0) should ===(0)
      concat0.indexOf(0xFF.toByte, 17) should ===(18)
      concat0.indexOf(0xFE.toByte, 17) should ===(-1)
    }
    "contains" in {
      ByteString.empty.contains(5) should ===(false)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.contains('a') should ===(true)
      byteString1.contains('c') should ===(true)
      byteString1.contains('d') should ===(false)
      val byteString2 = byteString1 ++ ByteString1.fromString("def")
      byteString2.contains('a') should ===(true)
      byteString2.contains('c') should ===(true)
      byteString2.contains('d') should ===(true)
      byteString2.contains('f') should ===(true)
      byteString1.contains('z') should ===(false)
    }
    "contains (specialized)" in {
      ByteString.empty.contains(5.toByte) should ===(false)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.contains('a'.toByte) should ===(true)
      byteString1.contains('c'.toByte) should ===(true)
      byteString1.contains('d'.toByte) should ===(false)
      val byteString2 = byteString1 ++ ByteString1.fromString("def")
      byteString2.contains('a'.toByte) should ===(true)
      byteString2.contains('c'.toByte) should ===(true)
      byteString2.contains('d'.toByte) should ===(true)
      byteString2.contains('f'.toByte) should ===(true)
      byteString1.contains('z'.toByte) should ===(false)
    }
    "indexOf from/to" in {
      ByteString.empty.indexOf(5.toByte, -1, 10) should ===(-1)
      ByteString.empty.indexOf(5.toByte, 0, 1) should ===(-1)
      ByteString.empty.indexOf(5.toByte, 1, -1) should ===(-1)
      val byteString1 = ByteString1.fromString("abc")
      byteString1.indexOf('d'.toByte, -1, 1) should ===(-1)
      byteString1.indexOf('a'.toByte, -1, 1) should ===(0)
      byteString1.indexOf('a'.toByte, 0, 1) should ===(0)
      byteString1.indexOf('a'.toByte, 0, 0) should ===(-1)
      byteString1.indexOf('a'.toByte, 1, 2) should ===(-1)

      val array = Array[Byte]('x', 'y', 'z', 'a', 'b', 'c')
      val byteString2 = ByteString1(array, 3, 2)
      byteString2.indexOf('x'.toByte, -1, 3) should ===(-1)
      byteString2.indexOf('x'.toByte, 0, 3) should ===(-1)
      byteString2.indexOf('a'.toByte, -1, 1) should ===(0)
      byteString2.indexOf('a'.toByte, 0, 0) should ===(-1)
      byteString2.indexOf('a'.toByte, 1, 2) should ===(-1)

      val byteStrings = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("efg"))
      byteStrings.indexOf('c'.toByte, -1, 6) should ===(2)
      byteStrings.indexOf('c'.toByte, 0, 6) should ===(2)
      byteStrings.indexOf('c'.toByte, 2, 3) should ===(2)
      byteStrings.indexOf('c'.toByte, 2, 2) should ===(-1)
      byteStrings.indexOf('c'.toByte, 3, 4) should ===(-1)

      byteStrings.indexOf('e'.toByte, -1, 6) should ===(3)
      byteStrings.indexOf('e'.toByte, 0, 4) should ===(3)
      byteStrings.indexOf('e'.toByte, 1, 4) should ===(3)
      byteStrings.indexOf('e'.toByte, 4, 5) should ===(-1)

      byteStrings.indexOf('g'.toByte, -1, 6) should ===(5)
      byteStrings.indexOf('g'.toByte, 0, 5) should ===(-1)

      val compact = byteStrings.compact
      compact.indexOf('c'.toByte, -1, 6) should ===(2)
      compact.indexOf('c'.toByte, 0, 6) should ===(2)
      compact.indexOf('c'.toByte, 2, 3) should ===(2)
      compact.indexOf('c'.toByte, 2, 2) should ===(-1)
      compact.indexOf('c'.toByte, 3, 4) should ===(-1)

      compact.indexOf('e'.toByte, -1, 6) should ===(3)
      compact.indexOf('e'.toByte, 0, 4) should ===(3)
      compact.indexOf('e'.toByte, 1, 4) should ===(3)
      compact.indexOf('e'.toByte, 4, 5) should ===(-1)

      compact.indexOf('g'.toByte, -1, 6) should ===(5)
      compact.indexOf('g'.toByte, 0, 5) should ===(-1)

      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      byteStringLong.indexOf('m', 2, 24) should ===(12)
      byteStringLong.indexOf('z', 2, 26) should ===(25)
      byteStringLong.indexOf('z', 2, 24) should ===(-1)
      byteStringLong.indexOf('a', 2, 24) should ===(-1)
    }
    "copyToArray" in {
      val byteString = ByteString(1, 2) ++ ByteString(3) ++ ByteString(4)

      def verify(f: Array[Byte] => Unit)(expected: Byte*): Unit = {
        val array = Array.fill[Byte](3)(0)
        f(array)
        array should ===(expected.toArray)
      }

      verify(byteString.copyToArray(_, 0, 1))(1, 0, 0)
      verify(byteString.copyToArray(_, 1, 1))(0, 1, 0)
      verify(byteString.copyToArray(_, 2, 1))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 1))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 2))(1, 2, 0)
      verify(byteString.copyToArray(_, 1, 2))(0, 1, 2)
      verify(byteString.copyToArray(_, 2, 2))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 2))(0, 0, 0)
      verify(byteString.copyToArray(_, 0, 3))(1, 2, 3)
      verify(byteString.copyToArray(_, 1, 3))(0, 1, 2)
      verify(byteString.copyToArray(_, 2, 3))(0, 0, 1)
      verify(byteString.copyToArray(_, 3, 3))(0, 0, 0)
    }
    "containsSlice" in {
      val slice0 = ByteString1.fromString("xyz")
      val slice1 = ByteString1.fromString("xyzabc")
      val notSlice = ByteString1.fromString("12345")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.containsSlice(slice0) should ===(true)
      byteStringLong.containsSlice(slice1) should ===(false)
      byteStringLong.containsSlice(notSlice) should ===(false)

      byteStrings.containsSlice(slice0) should ===(true)
      byteStrings.containsSlice(slice1) should ===(true)
      byteStrings.containsSlice(notSlice) should ===(false)
    }
    "indexOfSlice" in {
      val slice0 = ByteString1.fromString("xyz")
      val slice1 = ByteString1.fromString("xyzabc")
      val notSlice = ByteString1.fromString("12345")
      val pByte = ByteString1.fromString("p")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.indexOfSlice(slice0) should ===(23)
      byteStringLong.indexOfSlice(slice1) should ===(-1)
      byteStringLong.indexOfSlice(notSlice) should ===(-1)
      byteStringLong.indexOfSlice(pByte) should ===(15)

      byteStrings.indexOfSlice(slice0) should ===(23)
      byteStrings.indexOfSlice(slice1) should ===(23)
      byteStrings.indexOfSlice(notSlice) should ===(-1)
      byteStrings.indexOfSlice(pByte) should ===(15)
      byteStrings.indexOfSlice(pByte, 16) should ===(41)

      val byteStringXxyz = ByteString1.fromString("xxyz")
      byteStringXxyz.indexOfSlice(slice0) should ===(1)
      byteStringXxyz.indexOfSlice(slice0, -1) should ===(1)
      byteStringXxyz.indexOfSlice(slice0, 2) should ===(-1)
      byteStringXxyz.indexOfSlice(Seq.empty) should ===(0)
      byteStringXxyz.indexOfSlice(Seq.empty, -1) should ===(0)
      byteStringXxyz.indexOfSlice(Seq.empty, 1) should ===(1)

      // empty slice at from == length: should return length (consistent with Scala standard library)
      byteStringXxyz.indexOfSlice(Seq.empty, 4) should ===(4)
      // empty slice at from > length: should return -1
      byteStringXxyz.indexOfSlice(Seq.empty, 5) should ===(-1)

      // Empty source
      ByteString.empty.indexOfSlice(Seq.empty) should ===(0)
      ByteString.empty.indexOfSlice(ByteString1.fromString("a")) should ===(-1)

      // Slice equals entire source
      ByteString1.fromString("abc").indexOfSlice(ByteString1.fromString("abc")) should ===(0)

      // Slice longer than source
      ByteString1.fromString("ab").indexOfSlice(ByteString1.fromString("abc")) should ===(-1)

      // Cross-segment match in ByteStrings: "bc" spans segment boundary of ("ab" ++ "cd")
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
        .indexOfSlice(ByteString1.fromString("bc")) should ===(1)

      // False match: first byte matches multiple times before the full slice matches
      // "ab" in "aabc": 'a' at index 0 (check: bytes[1]='a' != 'b' ✗), then 'a' at 1 (bytes[2]='b' == 'b' ✓) -> 1
      ByteString1.fromString("aabc").indexOfSlice(ByteString1.fromString("ab")) should ===(1)
    }
    "indexOfSlice (specialized)" in {
      val slice0 = "xyz".getBytes(StandardCharsets.UTF_8)
      val slice1 = "xyzabc".getBytes(StandardCharsets.UTF_8)
      val notSlice = "12345".getBytes(StandardCharsets.UTF_8)
      val pByte = Array('p'.toByte)
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.indexOfSlice(slice0) should ===(23)
      byteStringLong.indexOfSlice(slice1) should ===(-1)
      byteStringLong.indexOfSlice(notSlice) should ===(-1)
      byteStringLong.indexOfSlice(pByte) should ===(15)

      byteStrings.indexOfSlice(slice0) should ===(23)
      byteStrings.indexOfSlice(slice1) should ===(23)
      byteStrings.indexOfSlice(notSlice) should ===(-1)
      byteStrings.indexOfSlice(pByte) should ===(15)
      byteStrings.indexOfSlice(pByte, 16) should ===(41)

      val byteStringXxyz = ByteString1.fromString("xxyz")
      byteStringXxyz.indexOfSlice(slice0) should ===(1)
      byteStringXxyz.indexOfSlice(slice0, -1) should ===(1)
      byteStringXxyz.indexOfSlice(slice0, 2) should ===(-1)
      byteStringXxyz.indexOfSlice(Seq.empty) should ===(0)
      byteStringXxyz.indexOfSlice(Seq.empty, -1) should ===(0)
      byteStringXxyz.indexOfSlice(Seq.empty, 1) should ===(1)

      // empty slice at from == length: should return length (consistent with Scala standard library)
      byteStringXxyz.indexOfSlice(Seq.empty, 4) should ===(4)
      // empty slice at from > length: should return -1
      byteStringXxyz.indexOfSlice(Seq.empty, 5) should ===(-1)

      // Empty source
      ByteString.empty.indexOfSlice(Array.empty[Byte]) should ===(0)
      ByteString.empty.indexOfSlice(Array[Byte]('a')) should ===(-1)

      // Slice equals entire source
      ByteString1.fromString("abc").indexOfSlice("abc".getBytes(StandardCharsets.UTF_8)) should ===(0)

      // Slice longer than source
      ByteString1.fromString("ab").indexOfSlice("abc".getBytes(StandardCharsets.UTF_8)) should ===(-1)

      // Cross-segment match in ByteStrings: "bc" spans segment boundary of ("ab" ++ "cd")
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
        .indexOfSlice(Array[Byte]('b', 'c')) should ===(1)

      // False match: first byte matches multiple times before the full slice matches
      ByteString1.fromString("aabc").indexOfSlice(Array[Byte]('a', 'b')) should ===(1)

      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 24)
      byteStringWithOffset.indexOfSlice(slice0) should ===(21)

      val concat0 = makeMultiByteStringsWithEmptyComponents()
      concat0.indexOfSlice(Array(15.toByte, 16.toByte)) should ===(16)
      concat0.indexOfSlice(Array(16.toByte, 15.toByte)) should ===(-1)
    }
    "lastIndexOfSlice" in {
      val slice0 = ByteString1.fromString("xyz")
      val slice1 = ByteString1.fromString("xyzabc")
      val notSlice = ByteString1.fromString("12345")
      val pByte = ByteString1.fromString("p")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.lastIndexOfSlice(slice0) should ===(23)
      byteStringLong.lastIndexOfSlice(slice1) should ===(-1)
      byteStringLong.lastIndexOfSlice(notSlice) should ===(-1)
      byteStringLong.lastIndexOfSlice(pByte) should ===(15)

      byteStrings.lastIndexOfSlice(slice0) should ===(49)
      byteStrings.lastIndexOfSlice(slice1) should ===(23)
      byteStrings.lastIndexOfSlice(notSlice) should ===(-1)
      byteStrings.lastIndexOfSlice(pByte) should ===(41)
      byteStrings.lastIndexOfSlice(pByte, 40) should ===(15)

      val byteStringXxyz = ByteString1.fromString("xxyz")
      byteStringXxyz.lastIndexOfSlice(slice0) should ===(1)
      byteStringXxyz.lastIndexOfSlice(slice0, 10) should ===(1)
      byteStringXxyz.lastIndexOfSlice(slice0, 0) should ===(-1)
      byteStringXxyz.lastIndexOfSlice(slice0, 2) should ===(1)
      byteStringXxyz.lastIndexOfSlice(Seq.empty) should ===(4)
      byteStringXxyz.lastIndexOfSlice(Seq.empty, 2) should ===(2)

      val xyzx = ByteString1.fromString("xyzx")
      xyzx.lastIndexOfSlice(slice0) should ===(0)

      // Empty source with empty slice -> 0; with non-empty slice -> -1
      ByteString.empty.lastIndexOfSlice(ByteString.empty) should ===(0)
      ByteString.empty.lastIndexOfSlice(ByteString1.fromString("a")) should ===(-1)

      // Slice equals entire source -> 0
      ByteString1.fromString("abc").lastIndexOfSlice(ByteString1.fromString("abc")) should ===(0)

      // Slice longer than source -> -1
      ByteString1.fromString("ab").lastIndexOfSlice(ByteString1.fromString("abc")) should ===(-1)

      // end = -1 for non-empty slice -> -1
      ByteString1.fromString("abc").lastIndexOfSlice(ByteString1.fromString("a"), -1) should ===(-1)

      // False-match retry: tail byte 'b' matches at index 2, but bytes[1]='b' != 'a'; retries, finds at index 1, bytes[0]='a' == 'a'
      ByteString1.fromString("abb").lastIndexOfSlice(ByteString1.fromString("ab")) should ===(0)

      // Repeated pattern: "aa" in "aaaa" -> last occurrence starts at index 2
      ByteString1.fromString("aaaa").lastIndexOfSlice(ByteString1.fromString("aa")) should ===(2)

      // Cross-segment match in ByteStrings: slice "bc" spans segment boundary of ("ab" ++ "cd")
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
        .lastIndexOfSlice(ByteString1.fromString("bc")) should ===(1)
    }
    "lastIndexOfSlice (specialized)" in {
      val slice0 = "xyz".getBytes(StandardCharsets.UTF_8)
      val slice1 = "xyzabc".getBytes(StandardCharsets.UTF_8)
      val notSlice = "12345".getBytes(StandardCharsets.UTF_8)
      val pByte = Array('p'.toByte)
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.lastIndexOfSlice(slice0) should ===(23)
      byteStringLong.lastIndexOfSlice(slice1) should ===(-1)
      byteStringLong.lastIndexOfSlice(notSlice) should ===(-1)
      byteStringLong.lastIndexOfSlice(pByte) should ===(15)

      byteStrings.lastIndexOfSlice(slice0) should ===(49)
      byteStrings.lastIndexOfSlice(slice1) should ===(23)
      byteStrings.lastIndexOfSlice(notSlice) should ===(-1)
      byteStrings.lastIndexOfSlice(pByte) should ===(41)
      byteStrings.lastIndexOfSlice(pByte, 40) should ===(15)

      val byteStringXxyz = ByteString1.fromString("xxyz")
      byteStringXxyz.lastIndexOfSlice(slice0) should ===(1)
      byteStringXxyz.lastIndexOfSlice(slice0, 10) should ===(1)
      byteStringXxyz.lastIndexOfSlice(slice0, 0) should ===(-1)
      byteStringXxyz.lastIndexOfSlice(slice0, 2) should ===(1)
      byteStringXxyz.lastIndexOfSlice(Seq.empty) should ===(4)
      byteStringXxyz.lastIndexOfSlice(Seq.empty, 2) should ===(2)

      val xyzx = ByteString1.fromString("xyzx")
      xyzx.lastIndexOfSlice(slice0) should ===(0)

      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 24)
      byteStringWithOffset.lastIndexOfSlice(slice0) should ===(21)

      val concat0 = makeMultiByteStringsSample()
      concat0.lastIndexOfSlice(Array(16.toByte, 0xFF.toByte)) should ===(17)
      concat0.lastIndexOfSlice(Array(16.toByte, 0xFE.toByte)) should ===(-1)

      val concat1 = makeMultiByteStringsWithEmptyComponents()
      concat1.lastIndexOfSlice(Array(15.toByte, 16.toByte)) should ===(16)
      concat1.lastIndexOfSlice(Array(16.toByte, 15.toByte)) should ===(-1)

      // Empty source with empty slice -> 0; with non-empty slice -> -1
      ByteString.empty.lastIndexOfSlice(Array.empty[Byte]) should ===(0)
      ByteString.empty.lastIndexOfSlice(Array[Byte]('a')) should ===(-1)

      // Slice equals entire source -> 0
      ByteString1.fromString("abc").lastIndexOfSlice("abc".getBytes(StandardCharsets.UTF_8)) should ===(0)

      // Slice longer than source -> -1
      ByteString1.fromString("ab").lastIndexOfSlice("abc".getBytes(StandardCharsets.UTF_8)) should ===(-1)

      // end = -1 for non-empty slice -> -1
      ByteString1.fromString("abc").lastIndexOfSlice(Array[Byte]('a'), -1) should ===(-1)

      // False-match retry: tail byte 'b' matches at index 2, but bytes[1]='b' != 'a'; retries, finds at index 1
      ByteString1.fromString("abb").lastIndexOfSlice(Array[Byte]('a', 'b')) should ===(0)

      // Repeated pattern: "aa" in "aaaa" -> last occurrence starts at index 2
      ByteString1.fromString("aaaa").lastIndexOfSlice(Array[Byte]('a', 'a')) should ===(2)

      // Cross-segment match in ByteStrings: slice "bc" spans segment boundary of ("ab" ++ "cd")
      ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
        .lastIndexOfSlice(Array[Byte]('b', 'c')) should ===(1)
    }
    "startsWith" in {
      val slice0 = ByteString1.fromString("abcdefghijk")
      val slice1 = ByteString1.fromString("xyz")
      val slice2 = ByteString1.fromString("zabcdefghijk")
      val notSlice = ByteString1.fromString("12345")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.startsWith(slice0) should ===(true)
      byteStringLong.startsWith(slice1, 23) should ===(true)
      byteStringLong.startsWith(notSlice) should ===(false)

      byteStrings.startsWith(slice0) should ===(true)
      byteStrings.startsWith(slice1, 23) should ===(true)
      byteStrings.startsWith(slice2, 25) should ===(true)
      byteStrings.startsWith(notSlice) should ===(false)

      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 20)
      val slice3 = ByteString1.fromString("cdefghijklmn")
      byteStringWithOffset.startsWith(slice3) should ===(true)

      // empty bytes array always returns true
      byteStringLong.startsWith(Array.emptyByteArray) should ===(true)
      byteStrings.startsWith(Array.emptyByteArray) should ===(true)

      // exact match
      val fullSliceText = "abcdefghijklmnopqrstuvwxyz"
      val fullSlice = ByteString1.fromString(fullSliceText)
      byteStringLong.startsWith(fullSlice) should ===(true)
      byteStringLong.startsWith(fullSliceText) should ===(true)

      // bytes longer than ByteString returns false
      val tooLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz1")
      byteStringLong.startsWith(tooLong) should ===(false)

      // ByteString1C
      val byteString1C = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      byteString1C.startsWith(slice0) should ===(true)
      byteString1C.startsWith(notSlice) should ===(false)
      byteString1C.startsWith(Array.emptyByteArray) should ===(true)

      // empty ByteString
      ByteString.empty.startsWith(Array.emptyByteArray) should ===(true)
      ByteString.empty.startsWith(ByteString1.fromString("a")) should ===(false)
      ByteString.empty.startsWith("a") should ===(false)
    }
    "startsWith (specialized)" in {
      val slice0 = "abcdefghijk".getBytes(StandardCharsets.UTF_8)
      val slice1 = "xyz".getBytes(StandardCharsets.UTF_8)
      val slice2 = "zabcdefghijk".getBytes(StandardCharsets.UTF_8)
      val notSlice = "12345".getBytes(StandardCharsets.UTF_8)
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      byteStringLong.startsWith(slice0) should ===(true)
      byteStringLong.startsWith(slice1, 23) should ===(true)
      byteStringLong.startsWith(notSlice) should ===(false)

      byteStrings.startsWith(slice0) should ===(true)
      byteStrings.startsWith(slice1, 23) should ===(true)
      byteStrings.startsWith(slice2, 25) should ===(true)
      byteStrings.startsWith(notSlice) should ===(false)

      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 20)
      val slice3 = "cdefghijklmn".getBytes(StandardCharsets.UTF_8)
      byteStringWithOffset.startsWith(slice3) should ===(true)

      // empty bytes array always returns true
      byteStringLong.startsWith(Array.emptyByteArray) should ===(true)
      byteStrings.startsWith(Array.emptyByteArray) should ===(true)

      // exact match
      val fullSlice = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(fullSlice) should ===(true)

      // bytes longer than ByteString returns false
      val tooLong = "abcdefghijklmnopqrstuvwxyz1".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(tooLong) should ===(false)

      // ByteString1C
      val byteString1C = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      byteString1C.startsWith(slice0) should ===(true)
      byteString1C.startsWith(notSlice) should ===(false)
      byteString1C.startsWith(Array.emptyByteArray) should ===(true)

      // empty ByteString
      ByteString.empty.startsWith(Array.emptyByteArray) should ===(true)
      ByteString.empty.startsWith(Array[Byte]('a')) should ===(false)

      val concat0 = makeMultiByteStringsWithEmptyComponents()
      concat0.startsWith(Array(0xFF.toByte, 0.toByte, 1.toByte)) should ===(true)
      concat0.startsWith(Array(0xFF.toByte, 1.toByte)) should ===(false)

      // SWAR-optimised path: needles spanning full 8-byte chunks (ByteString1)
      // exactly 8 bytes: one SWAR iteration, no tail
      val exactly8 = "abcdefgh".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(exactly8) should ===(true)
      byteStringLong.startsWith("12345678".getBytes(StandardCharsets.UTF_8)) should ===(false)
      // 16 bytes: two SWAR iterations, no tail
      val exactly16 = "abcdefghijklmnop".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(exactly16) should ===(true)
      byteStringLong.startsWith("abcdefghijklmnop".reverse.getBytes(StandardCharsets.UTF_8)) should ===(false)
      // 9 bytes: one SWAR iteration + 1-byte tail
      val nine = "abcdefghi".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(nine) should ===(true)
      // mismatch buried inside the 2nd 8-byte chunk
      val mismatchInSecondChunk = "abcdefghijklmno_".getBytes(StandardCharsets.UTF_8)
      byteStringLong.startsWith(mismatchInSecondChunk) should ===(false)
      // ByteString1 with startsWith(Array[Byte], offset) exercising offset != 0
      val bs1WithOffset = ByteString1("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 0, 26)
      bs1WithOffset.startsWith("ijklmnop".getBytes(StandardCharsets.UTF_8), 8) should ===(true)
      bs1WithOffset.startsWith("12345678".getBytes(StandardCharsets.UTF_8), 8) should ===(false)
      // ByteString1C SWAR path
      val bs1c = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      bs1c.startsWith(exactly8) should ===(true)
      bs1c.startsWith(exactly16) should ===(true)
      bs1c.startsWith(nine) should ===(true)
      bs1c.startsWith("12345678".getBytes(StandardCharsets.UTF_8)) should ===(false)
      bs1c.startsWith("abcdefghi".getBytes(StandardCharsets.UTF_8), 0) should ===(true)
      bs1c.startsWith("bcdefghi".getBytes(StandardCharsets.UTF_8), 1) should ===(true)
      bs1c.startsWith("12345678".getBytes(StandardCharsets.UTF_8), 1) should ===(false)
    }
    "endsWith" in {
      val suffix0 = ByteString1.fromString("uvwxyz")
      val suffix1 = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val notSuffix = ByteString1.fromString("12345")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)

      // ByteString1 basic cases
      byteStringLong.endsWith(suffix0) should ===(true)
      byteStringLong.endsWith(notSuffix) should ===(false)

      // exact match
      byteStringLong.endsWith(suffix1) should ===(true)

      // bytes longer than ByteString returns false
      val tooLong = ByteString1.fromString("0abcdefghijklmnopqrstuvwxyz")
      byteStringLong.endsWith(tooLong) should ===(false)

      // empty bytes array always returns true
      byteStringLong.endsWith(Array.emptyByteArray) should ===(true)

      // ByteStrings (multi-segment)
      byteStrings.endsWith(suffix0) should ===(true)
      byteStrings.endsWith(notSuffix) should ===(false)
      byteStrings.endsWith(Array.emptyByteArray) should ===(true)

      // suffix spanning the segment boundary
      val crossBoundary = ByteString1.fromString("xyzabcdefghijklmnopqrstuvwxyz")
      byteStrings.endsWith(crossBoundary) should ===(true)

      // ByteString1C
      val byteString1C = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      byteString1C.endsWith(suffix0) should ===(true)
      byteString1C.endsWith(notSuffix) should ===(false)
      byteString1C.endsWith(Array.emptyByteArray) should ===(true)

      // ByteString1 with internal offset
      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 20)
      // ByteString1(bytes, 2, 20) represents "cdefghijklmnopqrstuv"
      val offsetSuffixText = "rstuv"
      val offsetSuffix = ByteString1.fromString(offsetSuffixText)
      byteStringWithOffset.endsWith(offsetSuffix) should ===(true)
      byteStringWithOffset.endsWith(offsetSuffixText) should ===(true)
      byteStringWithOffset.endsWith(notSuffix) should ===(false)

      // empty ByteString
      ByteString.empty.endsWith(Array.emptyByteArray) should ===(true)
      ByteString.empty.endsWith(ByteString1.fromString("a")) should ===(false)
      ByteString.empty.endsWith("a") should ===(false)
    }
    "endsWith (specialized)" in {
      val suffix0 = "uvwxyz".getBytes(StandardCharsets.UTF_8)
      val suffix1 = "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      val notSuffix = "12345".getBytes(StandardCharsets.UTF_8)
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)

      // ByteString1 basic cases
      byteStringLong.endsWith(suffix0) should ===(true)
      byteStringLong.endsWith(notSuffix) should ===(false)

      // exact match
      byteStringLong.endsWith(suffix1) should ===(true)

      // bytes longer than ByteString returns false
      val tooLong = "0abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong.endsWith(tooLong) should ===(false)

      // empty bytes array always returns true
      byteStringLong.endsWith(Array.emptyByteArray) should ===(true)

      // ByteStrings (multi-segment)
      byteStrings.endsWith(suffix0) should ===(true)
      byteStrings.endsWith(notSuffix) should ===(false)
      byteStrings.endsWith(Array.emptyByteArray) should ===(true)

      // suffix spanning the segment boundary
      val crossBoundary = "xyzabcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStrings.endsWith(crossBoundary) should ===(true)

      // ByteString1C
      val byteString1C = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      byteString1C.endsWith(suffix0) should ===(true)
      byteString1C.endsWith(notSuffix) should ===(false)
      byteString1C.endsWith(Array.emptyByteArray) should ===(true)

      // ByteString1 with internal offset
      val byteStringWithOffset = ByteString1(
        "abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 20)
      // ByteString1(bytes, 2, 20) represents "cdefghijklmnopqrstuv"
      val offsetSuffix = "rstuv".getBytes(StandardCharsets.UTF_8)
      byteStringWithOffset.endsWith(offsetSuffix) should ===(true)
      byteStringWithOffset.endsWith(notSuffix) should ===(false)

      // empty ByteString
      ByteString.empty.endsWith(Array.emptyByteArray) should ===(true)
      ByteString.empty.endsWith(Array[Byte]('a')) should ===(false)

      val concat1 = makeMultiByteStringsWithEmptyComponents()
      concat1.endsWith(Array[Byte](16.toByte, 0xFF.toByte)) should ===(true)
      concat1.endsWith(Array[Byte](15.toByte, 0xFF.toByte)) should ===(false)

      // SWAR-optimised path: needles spanning full 8-byte chunks (ByteString1)
      val byteStringLong2 = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      // exactly 8 bytes: one SWAR iteration, no tail
      val last8 = "stuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong2.endsWith(last8) should ===(true)
      byteStringLong2.endsWith("12345678".getBytes(StandardCharsets.UTF_8)) should ===(false)
      // 16 bytes: two SWAR iterations, no tail
      val last16 = "klmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong2.endsWith(last16) should ===(true)
      byteStringLong2.endsWith("klmnopqrstuvwxy_".getBytes(StandardCharsets.UTF_8)) should ===(false)
      // 9 bytes: one SWAR iteration + 1-byte tail
      val last9 = "rstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong2.endsWith(last9) should ===(true)
      // mismatch buried inside the first 8-byte chunk
      val mismatchInFirstChunk = "_lmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8)
      byteStringLong2.endsWith(mismatchInFirstChunk) should ===(false)
      // ByteString1 with internal offset
      val bs1WithOffset2 = ByteString1("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8), 2, 20)
      // represents "cdefghijklmnopqrstuv"
      bs1WithOffset2.endsWith("mnopqrstuv".getBytes(StandardCharsets.UTF_8)) should ===(true)
      bs1WithOffset2.endsWith("12345678".getBytes(StandardCharsets.UTF_8)) should ===(false)
      // ByteString1C SWAR path
      val bs1c2 = ByteString1C("abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.UTF_8))
      bs1c2.endsWith(last8) should ===(true)
      bs1c2.endsWith(last16) should ===(true)
      bs1c2.endsWith(last9) should ===(true)
      bs1c2.endsWith("12345678".getBytes(StandardCharsets.UTF_8)) should ===(false)
      bs1c2.endsWith(mismatchInFirstChunk) should ===(false)
    }
    "return same hashCode" in {
      val slice0 = ByteString1.fromString("xyz")
      val slice1 = ByteString1.fromString("xyzabc")
      val slice2 = ByteString1.fromString("12345")
      val byteStringLong = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStringLong2 = ByteString1.fromString("abcdefghijklmnopqrstuvwxyz")
      val byteStrings = ByteStrings(byteStringLong, byteStringLong)
      val byteStrings2 = ByteStrings(byteStringLong, byteStringLong)
      slice0.hashCode should ===(slice0.hashCode)
      slice1.hashCode should ===(slice1.hashCode)
      slice2.hashCode should ===(slice2.hashCode)
      byteStringLong.hashCode should ===(byteStringLong.hashCode)
      byteStringLong.hashCode should ===(byteStringLong2.hashCode)
      byteStringLong2.equals(byteStringLong) should ===(true)
      byteStrings.hashCode should ===(byteStrings.hashCode)
      byteStrings.hashCode should ===(byteStrings2.hashCode)
      byteStrings2.equals(byteStrings) should ===(true)
    }
  }

  "A ByteString" must {
    "have correct size" when {
      "concatenating" in { check((a: ByteString, b: ByteString) => (a ++ b).size == a.size + b.size) }
      "dropping" in { check((a: ByteString, b: ByteString) => (a ++ b).drop(b.size).size == a.size) }
      "taking" in { check((a: ByteString, b: ByteString) => (a ++ b).take(a.size) == a) }
      "takingRight" in { check((a: ByteString, b: ByteString) => (a ++ b).takeRight(b.size) == b) }
      "dropping then taking" in {
        check((a: ByteString, b: ByteString) => (b ++ a ++ b).drop(b.size).take(a.size) == a)
      }
      "droppingRight" in { check((a: ByteString, b: ByteString) => (b ++ a ++ b).drop(b.size).dropRight(b.size) == a) }
    }

    "be sequential" when {
      "taking" in { check((a: ByteString, b: ByteString) => (a ++ b).take(a.size) == a) }
      "dropping" in { check((a: ByteString, b: ByteString) => (a ++ b).drop(a.size) == b) }
    }

    "be equal to the original" when {
      "compacting" in {
        check { (xs: ByteString) =>
          val ys = xs.compact; (xs == ys) && ys.isCompact
        }
      }
      "recombining" in {
        check { (xs: ByteString, from: Int, until: Int) =>
          val (tmp, c) = xs.splitAt(until)
          val (a, b) = tmp.splitAt(from)
          (a ++ b ++ c) == xs
        }
      }
      def excerciseRecombining(xs: ByteString, from: Int, until: Int) = {
        val (tmp, c) = xs.splitAt(until)
        val (a, b) = tmp.splitAt(from)
        (a ++ b ++ c) should ===(xs)
      }
      "recombining - edge cases" in {
        excerciseRecombining(
          ByteStrings(Vector(ByteString1(Array[Byte](1)), ByteString1(Array[Byte](2)))),
          -2147483648,
          112121212)
        excerciseRecombining(ByteStrings(Vector(ByteString1(Array[Byte](100)))), 0, 2)
        excerciseRecombining(ByteStrings(Vector(ByteString1(Array[Byte](100)))), -2147483648, 2)
        excerciseRecombining(ByteStrings(Vector(ByteString1.fromString("ab"), ByteString1.fromString("cd"))), 0, 1)
        excerciseRecombining(ByteString1.fromString("abc").drop(1).take(1), -324234, 234232)
        excerciseRecombining(ByteString("a"), 0, 2147483647)
        excerciseRecombining(
          ByteStrings(Vector(ByteString1.fromString("ab"), ByteString1.fromString("cd"))).drop(2),
          2147483647,
          1)
        excerciseRecombining(ByteString1.fromString("ab").drop1(1), Int.MaxValue, Int.MaxValue)
      }
    }

    "behave as expected" when {
      "created from and decoding to String" in {
        check { (s: String) =>
          ByteString(s, StandardCharsets.UTF_8).decodeString(StandardCharsets.UTF_8) == s
        }
      }

      "taking its own length" in {
        check { (b: ByteString) =>
          b.take(b.length) eq b
        }
      }

      "created from and decoding to Base64" in {
        check { (a: ByteString) =>
          val encoded = a.encodeBase64
          encoded == ByteString(java.util.Base64.getEncoder.encode(a.toArray)) &&
          encoded.decodeBase64 == a
        }
      }

      "created from iterator" in {
        check { (a: ByteString) =>
          val asIterator = a.iterator
          ByteString(asIterator) == a
        }
      }

      "compacting" in {
        check { (a: ByteString) =>
          val wasCompact = a.isCompact
          val b = a.compact
          ((!wasCompact) || (b eq a)) &&
          (b == a) &&
          b.isCompact &&
          (b.compact eq b)
        }
      }

      "asByteBuffers" in {
        check { (a: ByteString) =>
          if (a.isCompact) a.asByteBuffers.size == 1 && a.asByteBuffers.head == a.asByteBuffer
          else a.asByteBuffers.size > 0
        }
        check { (a: ByteString) =>
          a.asByteBuffers.foldLeft(ByteString.empty) { (bs, bb) =>
            bs ++ ByteString(bb)
          } == a
        }
        check { (a: ByteString) =>
          a.asByteBuffers.forall(_.isReadOnly)
        }
        check { (a: ByteString) =>
          import scala.jdk.CollectionConverters._
          a.asByteBuffers.zip(a.getByteBuffers().asScala).forall(x => x._1 == x._2)
        }
      }

      "toString should start with ByteString(" in {
        check { (bs: ByteString) =>
          bs.toString.startsWith("ByteString(")
        }
      }
    }
    "behave like a Vector" when {
      "concatenating" in {
        check { (a: ByteString, b: ByteString) =>
          likeVectors(a, b) { _ ++ _ }
        }
      }

      "calling apply" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, i1, i2) =>
              likeVector(xs) { seq =>
                (if ((i1 >= 0) && (i1 < seq.length)) seq(i1) else 0, if ((i2 >= 0) && (i2 < seq.length)) seq(i2) else 0)
              }
          }
        }
      }

      "calling head" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVector(a) { _.head }
        }
      }
      "calling tail" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVector(a) { _.tail }
        }
      }
      "calling last" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVector(a) { _.last }
        }
      }
      "calling init" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVector(a) { _.init }
        }
      }
      "calling length" in {
        check { (a: ByteString) =>
          likeVector(a) { _.length }
        }
      }

      "calling span" in {
        check { (a: ByteString, b: Byte) =>
          likeVector(a) { _.span(_ != b) match { case (a, b) => (a, b) } }
        }
      }

      "calling takeWhile" in {
        check { (a: ByteString, b: Byte) =>
          likeVector(a) { _.takeWhile(_ != b) }
        }
      }
      "calling dropWhile" in {
        check { (a: ByteString, b: Byte) =>
          likeVector(a) { _.dropWhile(_ != b) }
        }
      }
      "calling indexWhere" in {
        check { (a: ByteString, b: Byte) =>
          likeVector(a) { _.indexWhere(_ == b) }
        }
      }
      "calling indexWhere(p, idx)" in {
        check { (a: ByteString, b: Byte, idx: Int) =>
          likeVector(a) { _.indexWhere(_ == b, math.max(0, idx)) }
        }
      }
      "calling indexOf" in {
        check { (a: ByteString, b: Byte) =>
          likeVector(a) { _.indexOf(b) }
        }
      }
      // this actually behave weird for Vector and negative indexes - SI9936, fixed in Scala 2.12
      // so let's just skip negative indexes (doesn't make much sense anyway)
      "calling indexOf(elem, idx)" in {
        check { (a: ByteString, b: Byte, idx: Int) =>
          likeVector(a) { _.indexOf(b, math.max(0, idx)) }
        }
      }

      "calling foreach" in {
        check { (a: ByteString) =>
          likeVector(a) { it =>
            var acc = 0; it.foreach { acc += _ }; acc
          }
        }
      }
      "calling foldLeft" in {
        check { (a: ByteString) =>
          likeVector(a) { _.foldLeft(0) { _ + _ } }
        }
      }
      "calling toArray" in {
        check { (a: ByteString) =>
          likeVector(a) { _.toArray.toSeq }
        }
      }

      "calling slice" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVector(xs) {
                _.slice(from, until)
              }
          }
        }
      }

      "calling take and drop" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVector(xs) {
                _.drop(from).take(until - from)
              }
          }
        }
      }

      "calling grouped" in {
        check { (grouped: ByteStringGrouped) =>
          likeVector(grouped.bs) {
            _.grouped(grouped.size).toIndexedSeq
          }
        }
      }

      "calling copyToArray" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVector(xs) { it =>
                val array = new Array[Byte](xs.length)
                it.copyToArray(array, from, until)
                array.toSeq
              }
          }
        }
      }
    }

    "serialize correctly" when {
      "given all types of ByteString" in {
        check { (bs: ByteString) =>
          testSer(bs)
        }
      }

      "with a large concatenated bytestring" in {
        // coverage for #20901
        val original = ByteString(Array.fill[Byte](1000)(1)) ++ ByteString(Array.fill[Byte](1000)(2))

        deserialize(serialize(original)) shouldEqual original
      }
    }

    "unsafely wrap and unwrap bytes" in {
      // optimal case
      val bytes = Array.fill[Byte](100)(7)
      val bs = ByteString.fromArrayUnsafe(bytes)
      val bytes2 = bs.toArrayUnsafe()
      (bytes2 should be).theSameInstanceAs(bytes)

      val combinedBs = bs ++ bs
      val combinedBytes = combinedBs.toArrayUnsafe()
      combinedBytes should ===(bytes ++ bytes)
    }

    "read short values" in {
      val data = Array[Byte](1, 2, 3, 4)
      val byteString1C = ByteString1C(data)
      byteString1C.readShortBE(0) should ===(0x0102.toShort)
      byteString1C.readShortLE(0) should ===(0x0201.toShort)
      byteString1C.readShortBE(2) should ===(0x0304.toShort)
      byteString1C.readShortLE(2) should ===(0x0403.toShort)

      val arr = Array[Byte](0, 1, 2, 3, 4, 5)
      val byteString1 = ByteString1(arr, 2, 4)
      byteString1.readShortBE(0) should ===(0x0203.toShort)
      byteString1.readShortLE(0) should ===(0x0302.toShort)
      byteString1.readShortBE(2) should ===(0x0405.toShort)
      byteString1.readShortLE(2) should ===(0x0504.toShort)

      val byteStrings = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      byteStrings.readShortBE(0) should ===(0x6162.toShort)
      byteStrings.readShortLE(0) should ===(0x6261.toShort)
      byteStrings.readShortBE(2) should ===(0x6364.toShort)
      byteStrings.readShortLE(2) should ===(0x6463.toShort)
    }

    "read int values" in {
      val data = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8)
      val byteString1C = ByteString1C(data)
      byteString1C.readIntBE(0) should ===(0x01020304)
      byteString1C.readIntLE(0) should ===(0x04030201)
      byteString1C.readIntBE(4) should ===(0x05060708)
      byteString1C.readIntLE(4) should ===(0x08070605)

      val arr = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
      val byteString1 = ByteString1(arr, 1, 8)
      byteString1.readIntBE(0) should ===(0x01020304)
      byteString1.readIntLE(0) should ===(0x04030201)
      byteString1.readIntBE(4) should ===(0x05060708)
      byteString1.readIntLE(4) should ===(0x08070605)

      val byteStrings = ByteStrings(
        ByteString1(Array[Byte](1, 2), 0, 2),
        ByteString1(Array[Byte](3, 4, 5, 6, 7, 8), 0, 6))
      byteStrings.readIntBE(0) should ===(0x01020304)
      byteStrings.readIntLE(0) should ===(0x04030201)
      byteStrings.readIntBE(4) should ===(0x05060708)
      byteStrings.readIntLE(4) should ===(0x08070605)
    }

    "read long values" in {
      val data = Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
      val byteString1C = ByteString1C(data)
      byteString1C.readLongBE(0) should ===(0x0102030405060708L)
      byteString1C.readLongLE(0) should ===(0x0807060504030201L)
      byteString1C.readLongBE(8) should ===(0x090A0B0C0D0E0F10L)
      byteString1C.readLongLE(8) should ===(0x100F0E0D0C0B0A09L)

      val arr = Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
      val byteString1 = ByteString1(arr, 1, 16)
      byteString1.readLongBE(0) should ===(0x0102030405060708L)
      byteString1.readLongLE(0) should ===(0x0807060504030201L)
      byteString1.readLongBE(8) should ===(0x090A0B0C0D0E0F10L)
      byteString1.readLongLE(8) should ===(0x100F0E0D0C0B0A09L)

      val byteStrings = ByteStrings(
        ByteString1(Array[Byte](1, 2, 3, 4), 0, 4),
        ByteString1(Array[Byte](5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16), 0, 12))
      byteStrings.readLongBE(0) should ===(0x0102030405060708L)
      byteStrings.readLongLE(0) should ===(0x0807060504030201L)
      byteStrings.readLongBE(8) should ===(0x090A0B0C0D0E0F10L)
      byteStrings.readLongLE(8) should ===(0x100F0E0D0C0B0A09L)
    }

    "throw IndexOutOfBoundsException for readShortBE/LE with insufficient data" in {
      val bs1C = ByteString1C(Array[Byte](1))
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readShortBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readShortLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readShortBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readShortLE(-1)

      val bs1 = ByteString1(Array[Byte](0, 1, 2), 1, 1)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readShortBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readShortLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readShortBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readShortLE(-1)

      val bss = ByteStrings(ByteString1.fromString("a"), ByteString1.fromString("b"))
      an[IndexOutOfBoundsException] should be thrownBy bss.readShortBE(1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readShortLE(1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readShortBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readShortLE(-1)
    }

    "throw IndexOutOfBoundsException for readIntBE/LE with insufficient data" in {
      val bs1C = ByteString1C(Array[Byte](1, 2, 3))
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readIntBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readIntLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readIntBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readIntLE(-1)

      val bs1 = ByteString1(Array[Byte](0, 1, 2, 3, 4), 1, 3)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readIntBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readIntLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readIntBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readIntLE(-1)

      val bss = ByteStrings(ByteString1.fromString("abc"), ByteString1.fromString("d"))
      an[IndexOutOfBoundsException] should be thrownBy bss.readIntBE(1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readIntLE(1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readIntBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readIntLE(-1)
    }

    "throw IndexOutOfBoundsException for readLongBE/LE with insufficient data" in {
      val bs1C = ByteString1C(Array[Byte](1, 2, 3, 4, 5, 6, 7))
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readLongBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readLongLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readLongBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1C.readLongLE(-1)

      val bs1 = ByteString1(Array[Byte](0, 1, 2, 3, 4, 5, 6, 7, 8), 1, 7)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readLongBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readLongLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readLongBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1.readLongLE(-1)

      val bss = ByteStrings(ByteString1.fromString("abcdef"), ByteString1.fromString("g"))
      an[IndexOutOfBoundsException] should be thrownBy bss.readLongBE(0)
      an[IndexOutOfBoundsException] should be thrownBy bss.readLongLE(0)
      an[IndexOutOfBoundsException] should be thrownBy bss.readLongBE(-1)
      an[IndexOutOfBoundsException] should be thrownBy bss.readLongLE(-1)
    }

    "have correct takeRight behaviour for boundary values" in {
      // empty ByteString
      ByteString.empty.takeRight(-1) should ===(ByteString.empty)
      ByteString.empty.takeRight(0) should ===(ByteString.empty)
      ByteString.empty.takeRight(1) should ===(ByteString.empty)
      // n <= 0
      ByteString1.fromString("abc").takeRight(-1) should ===(ByteString.empty)
      ByteString1.fromString("abc").takeRight(0) should ===(ByteString.empty)
      ByteString1C.fromString("abc").takeRight(-1) should ===(ByteString.empty)
      ByteString1C.fromString("abc").takeRight(0) should ===(ByteString.empty)
      val bssTR = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      bssTR.takeRight(-1) should ===(ByteString.empty)
      bssTR.takeRight(0) should ===(ByteString.empty)
      // n >= length
      ByteString1.fromString("abc").takeRight(3) should ===(ByteString("abc"))
      ByteString1.fromString("abc").takeRight(100) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").takeRight(3) should ===(ByteString("abc"))
      ByteString1C.fromString("abc").takeRight(100) should ===(ByteString("abc"))
      bssTR.takeRight(4) should ===(ByteString("abcd"))
      bssTR.takeRight(100) should ===(ByteString("abcd"))
      // n in range
      ByteString1.fromString("abcde").takeRight(2) should ===(ByteString("de"))
      ByteString1C.fromString("abcde").takeRight(2) should ===(ByteString("de"))
      bssTR.takeRight(3) should ===(ByteString("bcd"))
    }

    "throw IndexOutOfBoundsException when apply is called with an out-of-bounds index" in {
      val bs1C = ByteString1C(Array[Byte](1, 2, 3))
      an[IndexOutOfBoundsException] should be thrownBy bs1C(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1C(3)
      val bs1 = ByteString1(Array[Byte](0, 1, 2, 3, 4), 1, 3)
      an[IndexOutOfBoundsException] should be thrownBy bs1(-1)
      an[IndexOutOfBoundsException] should be thrownBy bs1(3)
      val bss = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      an[IndexOutOfBoundsException] should be thrownBy bss(-1)
      an[IndexOutOfBoundsException] should be thrownBy bss(4)
      an[IndexOutOfBoundsException] should be thrownBy ByteString.empty(0)
    }

    "return 0 from copyToArray when start >= destination length" in {
      val bs1C = ByteString1C(Array[Byte](1, 2, 3))
      val dest1C = new Array[Byte](3)
      bs1C.copyToArray(dest1C, 3, 2) should ===(0)
      bs1C.copyToArray(dest1C, 5, 2) should ===(0)
      val bs1 = ByteString1(Array[Byte](0, 1, 2, 3, 4), 1, 3)
      val dest1 = new Array[Byte](3)
      bs1.copyToArray(dest1, 3, 2) should ===(0)
      bs1.copyToArray(dest1, 5, 2) should ===(0)
      val bss = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      val destBss = new Array[Byte](4)
      bss.copyToArray(destBss, 4, 2) should ===(0)
      bss.copyToArray(destBss, 6, 2) should ===(0)
    }

    "correctly handle sizeHint with value smaller than already committed bytes" in {
      val builder = ByteString.newBuilder
      builder.putBytes(Array[Byte](1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
      // sizeHint smaller than already written should not throw or shrink
      noException should be thrownBy builder.sizeHint(3)
      noException should be thrownBy builder.sizeHint(0)
      noException should be thrownBy builder.sizeHint(-1)
      builder.result().length should ===(10)
    }

    "correctly handle grouped with size larger than length" in {
      val bs = ByteString1.fromString("abc")
      bs.grouped(10).toList should ===(List(ByteString("abc")))
      bs.grouped(3).toList should ===(List(ByteString("abc")))
      ByteString.empty.grouped(5).toList should ===(List.empty)
      val bss = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      bss.grouped(100).toList should ===(List(ByteString("abcd")))
    }

    "map bytes correctly on each concrete type" in {
      val inc: Byte => Byte = b => (b + 1).toByte
      // ByteString1C
      ByteString1C(Array[Byte](1, 2, 3)).map(inc) should ===(ByteString(Array[Byte](2, 3, 4)))
      // ByteString1 with offset
      ByteString1(Array[Byte](0, 1, 2, 3, 4), 1, 3).map(inc) should ===(ByteString(Array[Byte](2, 3, 4)))
      // ByteStrings
      val bss = ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))
      bss.map(b => (b + 1).toByte) should ===(ByteString("bcde"))
      // empty
      ByteString.empty.map(inc) should ===(ByteString.empty)
    }

    "foreach visits each byte in order on each concrete type" in {
      def collect(bs: ByteString): Seq[Byte] = {
        val buf = scala.collection.mutable.ArrayBuffer.empty[Byte]
        bs.foreach(buf += _)
        buf.toSeq
      }
      // ByteString1C
      collect(ByteString1C(Array[Byte](10, 20, 30))) should ===(Seq[Byte](10, 20, 30))
      // ByteString1 with internal offset
      collect(ByteString1(Array[Byte](0, 10, 20, 30, 40), 1, 3)) should ===(Seq[Byte](10, 20, 30))
      // ByteStrings (multi-segment)
      collect(ByteStrings(ByteString1.fromString("ab"), ByteString1.fromString("cd"))) should ===(
        Seq[Byte]('a', 'b', 'c', 'd'))
      // empty
      collect(ByteString.empty) should ===(Seq.empty[Byte])
    }

    "slice returns correct result on ByteString1 with all boundary combinations" in {
      val bs = ByteString1.fromString("abcde")
      bs.slice(0, 5) should ===(ByteString("abcde"))
      (bs.slice(0, 5) eq bs) should ===(true) // identity when full range
      bs.slice(1, 4) should ===(ByteString("bcd"))
      bs.slice(0, 0) should ===(ByteString.empty)
      bs.slice(-5, 3) should ===(ByteString("abc"))
      bs.slice(3, 100) should ===(ByteString("de"))
      bs.slice(-5, -1) should ===(ByteString.empty)
      bs.slice(4, 2) should ===(ByteString.empty) // from > until
      // ByteString1 with internal offset
      val bs2 = ByteString1(Array[Byte](0, 1, 2, 3, 4, 5, 6), 2, 4) // [2,3,4,5]
      bs2.slice(1, 3) should ===(ByteString(Array[Byte](3, 4)))
      bs2.slice(0, 4) should ===(ByteString(Array[Byte](2, 3, 4, 5)))
      (bs2.slice(0, 4) eq bs2) should ===(true)
      bs2.slice(-1, 2) should ===(ByteString(Array[Byte](2, 3)))
    }

    "indexOfSlice handles non-Byte typed Seq safely" in {
      // Seq[Int] is B >: Byte; should not throw ClassCastException
      val bs = ByteString("abc")
      // 'a'.toInt == 97 — value semantics comparison works for Byte == Int through ==
      val result = bs.indexOfSlice(Seq[Int](97, 98)) // 'a'=97, 'b'=98
      result should ===(0)
      bs.indexOfSlice(Seq[Int](99)) should ===(2) // 'c'=99
      bs.indexOfSlice(Seq[Int](100)) should ===(-1) // 'd'=100 not present
    }

    "lastIndexOfSlice handles non-Byte typed Seq safely" in {
      val bs = ByteString("aabb")
      val result = bs.lastIndexOfSlice(Seq[Int](97, 97)) // 'a'=97
      result should ===(0)
      bs.lastIndexOfSlice(Seq[Int](98)) should ===(3) // 'b'=98
      bs.lastIndexOfSlice(Seq[Int](100)) should ===(-1) // 'd'=100 not present
    }

    "ByteString1.apply factory returns canonical empty for non-positive length" in {
      (ByteString1(Array[Byte](1, 2, 3), 0, 0) should be).theSameInstanceAs(ByteString1.empty)
      (ByteString1(Array[Byte](1, 2, 3), 0, -1) should be).theSameInstanceAs(ByteString1.empty)
      (ByteString1(Array[Byte](1, 2, 3), 0, Int.MinValue) should be).theSameInstanceAs(ByteString1.empty)
    }

    "ByteStringBuilder.sizeHint does not shrink existing capacity" in {
      val builder = ByteString.newBuilder
      builder.sizeHint(100)
      builder.putByte(42)
      // sizeHint smaller than current capacity — should not fail or lose data
      noException should be thrownBy builder.sizeHint(10)
      noException should be thrownBy builder.sizeHint(0)
      builder.result() should ===(ByteString(42.toByte))
    }
  }

  "A ByteStringIterator" must {
    "behave like a buffered Vector Iterator" when {
      "concatenating" in {
        check { (a: ByteString, b: ByteString) =>
          likeVecIts(a, b) { (a, b) =>
            (a ++ b).toSeq
          }
        }
      }

      "calling head" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVecIt(a) { _.head }
        }
      }
      "calling next" in {
        check { (a: ByteString) =>
          a.isEmpty || likeVecIt(a) { _.next() }
        }
      }
      "calling hasNext" in {
        check { (a: ByteString) =>
          likeVecIt(a) { _.hasNext }
        }
      }
      "calling length" in {
        check { (a: ByteString) =>
          likeVecIt(a)(_.length, strict = false)
        }
      }
      "calling duplicate" in {
        check { (a: ByteString) =>
          likeVecIt(a)({ _.duplicate match { case (a, b) => (a.toSeq, b.toSeq) } }, strict = false)
        }
      }

      // Have to used toList instead of toSeq here, iterator.span (new in
      // Scala-2.9) seems to be broken in combination with toSeq for the
      // scala.collection default Iterator (see Scala issue SI-5838).
      "calling span" in {
        check { (a: ByteString, b: Byte) =>
          likeVecIt(a)({ _.span(_ != b) match { case (a, b) => (a.toList, b.toList) } }, strict = false)
        }
      }

      "calling takeWhile" in {
        check { (a: ByteString, b: Byte) =>
          likeVecIt(a)({ _.takeWhile(_ != b).toSeq }, strict = false)
        }
      }
      "calling dropWhile" in {
        check { (a: ByteString, b: Byte) =>
          likeVecIt(a) { _.dropWhile(_ != b).toSeq }
        }
      }
      "calling indexWhere" in {
        check { (a: ByteString, b: Byte) =>
          likeVecIt(a) { _.indexWhere(_ == b) }
        }
      }
      "calling indexOf" in {
        check { (a: ByteString, b: Byte) =>
          likeVecIt(a) { _.indexOf(b) }
        }
      }
      "calling toSeq" in {
        check { (a: ByteString) =>
          likeVecIt(a) { _.toSeq }
        }
      }
      "calling foreach" in {
        check { (a: ByteString) =>
          likeVecIt(a) { it =>
            var acc = 0; it.foreach { acc += _ }; acc
          }
        }
      }
      "calling foldLeft" in {
        check { (a: ByteString) =>
          likeVecIt(a) { _.foldLeft(0) { _ + _ } }
        }
      }
      "calling toArray" in {
        check { (a: ByteString) =>
          likeVecIt(a) { _.toArray.toSeq }
        }
      }

      "calling slice" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVecIt(xs)({
                  _.slice(from, until).toSeq
                }, strict = false)
          }
        }
      }

      "calling take and drop" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVecIt(xs)({
                  _.drop(from).take(until - from).toSeq
                }, strict = false)
          }
        }
      }

      "calling copyToArray" in {
        check { (slice: ByteStringSlice) =>
          slice match {
            case (xs, from, until) =>
              likeVecIt(xs)({ it =>
                  val array = new Array[Byte](xs.length)
                  it.slice(from, until).copyToArray(array, from, until)
                  array.toSeq
                }, strict = false)
          }
        }
      }
    }

    "function as expected" when {
      "getting Bytes, using getByte and getBytes" in {
        // mixing getByte and getBytes here for more rigorous testing
        check { (slice: ByteStringSlice) =>
          val (bytes, from, to) = slice
          val input = bytes.iterator
          val output = new Array[Byte](bytes.length)
          for (i <- 0 until from) output(i) = input.getByte
          input.getBytes(output, from, to - from)
          for (i <- to until bytes.length) output(i) = input.getByte
          (output.toSeq == bytes) && (input.isEmpty)
        }
      }

      "getting Bytes with a given length" in {
        check { (slice: ByteStringSlice) =>
          val (bytes, _, _) = slice
          val input = bytes.iterator
          (input.getBytes(bytes.length).toSeq == bytes) && input.isEmpty
        }
      }

      "getting ByteString with a given length" in {
        check { (slice: ByteStringSlice) =>
          val (bytes, _, _) = slice
          val input = bytes.iterator
          (input.getByteString(bytes.length) == bytes) && input.isEmpty
        }
      }

      "getting Bytes, using the InputStream wrapper" in {
        // combining skip and both read methods here for more rigorous testing
        check { (slice: ByteStringSlice) =>
          val (bytes, from, to) = slice
          val a = (0 max from) min bytes.length
          val b = (a max to) min bytes.length
          val input = bytes.iterator
          val output = new Array[Byte](bytes.length)

          input.asInputStream.skip(a)

          val toRead = b - a
          var (nRead, eof) = (0, false)
          while ((nRead < toRead) && !eof) {
            val n = input.asInputStream.read(output, a + nRead, toRead - nRead)
            if (n == -1) eof = true
            else nRead += n
          }
          if (eof) throw new RuntimeException("Unexpected EOF")

          for (i <- b until bytes.length) output(i) = input.asInputStream.read().toByte

          (output.toSeq.drop(a) == bytes.drop(a)) &&
          (input.asInputStream.read() == -1) &&
          ((output.length < 1) || (input.asInputStream.read(output, 0, 1) == -1))
        }
      }

      "calling copyToBuffer" in {
        check { (bytes: ByteString) =>
          import java.nio.ByteBuffer
          val buffer = ByteBuffer.allocate(bytes.size)
          bytes.copyToBuffer(buffer)
          buffer.flip()
          val array = new Array[Byte](bytes.size)
          buffer.get(array)
          bytes == array.toSeq
        }
      }

      "copying chunks to an array" in {
        val iterator = (ByteString("123") ++ ByteString("456")).iterator
        val array = Array.ofDim[Byte](6)
        iterator.copyToArray(array, 0, 2)
        iterator.copyToArray(array, 2, 2)
        iterator.copyToArray(array, 4, 2)
        assert(new String(array) === "123456")
      }

      "calling copyToArray with length passing end of destination" in {
        // Pre fix len passing the end of the destination would cause never ending loop inside iterator copyToArray
        val iterator = (ByteString(1, 2) ++ ByteString(3) ++ ByteString(4)).iterator
        val array = Array.fill[Byte](3)(0)
        iterator.copyToArray(array, 2, 2)
        array.toSeq should ===(Seq(0, 0, 1))
      }
    }

    "decode data correctly" when {
      "decoding Short in big-endian" in {
        check { (slice: ByteStringSlice) =>
          testShortDecoding(slice, BIG_ENDIAN)
        }
      }
      "decoding Short in little-endian" in {
        check { (slice: ByteStringSlice) =>
          testShortDecoding(slice, LITTLE_ENDIAN)
        }
      }
      "decoding Int in big-endian" in {
        check { (slice: ByteStringSlice) =>
          testIntDecoding(slice, BIG_ENDIAN)
        }
      }
      "decoding Int in little-endian" in {
        check { (slice: ByteStringSlice) =>
          testIntDecoding(slice, LITTLE_ENDIAN)
        }
      }
      "decoding Long in big-endian" in {
        check { (slice: ByteStringSlice) =>
          testLongDecoding(slice, BIG_ENDIAN)
        }
      }
      "decoding Long in little-endian" in {
        check { (slice: ByteStringSlice) =>
          testLongDecoding(slice, LITTLE_ENDIAN)
        }
      }
      "decoding Float in big-endian" in {
        check { (slice: ByteStringSlice) =>
          testFloatDecoding(slice, BIG_ENDIAN)
        }
      }
      "decoding Float in little-endian" in {
        check { (slice: ByteStringSlice) =>
          testFloatDecoding(slice, LITTLE_ENDIAN)
        }
      }
      "decoding Double in big-endian" in {
        check { (slice: ByteStringSlice) =>
          testDoubleDecoding(slice, BIG_ENDIAN)
        }
      }
      "decoding Double in little-endian" in {
        check { (slice: ByteStringSlice) =>
          testDoubleDecoding(slice, LITTLE_ENDIAN)
        }
      }
    }
  }

  "A ByteStringBuilder" must {
    "function like a VectorBuilder" when {
      "adding various contents using ++= and +=" in {
        check { (array1: Array[Byte], array2: Array[Byte], bs1: ByteString, bs2: ByteString, bs3: ByteString) =>
          likeVecBld { builder =>
            builder ++= array1
            bs1.foreach { b =>
              builder += b
            }
            builder ++= bs2
            bs3.foreach { b =>
              builder += b
            }
            builder ++= array2.toIndexedSeq
          }
        }
      }
    }
    "function as expected" when {
      "putting Bytes, using putByte and putBytes" in {
        // mixing putByte and putBytes here for more rigorous testing
        check { (slice: ArraySlice[Byte]) =>
          val (data, from, to) = slice
          val builder = ByteString.newBuilder
          for (i <- 0 until from) builder.putByte(data(i))
          builder.putBytes(data, from, to - from)
          for (i <- to until data.length) builder.putByte(data(i))
          data.toSeq == builder.result()
        }
      }

      "putting Bytes, using the OutputStream wrapper" in {
        // mixing the write methods here for more rigorous testing
        check { (slice: ArraySlice[Byte]) =>
          val (data, from, to) = slice
          val builder = ByteString.newBuilder
          for (i <- 0 until from) builder.asOutputStream.write(data(i).toInt)
          builder.asOutputStream.write(data, from, to - from)
          for (i <- to until data.length) builder.asOutputStream.write(data(i).toInt)
          data.toSeq == builder.result()
        }
      }
    }

    "encode data correctly" when {
      "encoding Short in big-endian" in {
        check { (slice: ArraySlice[Short]) =>
          testShortEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding Short in little-endian" in {
        check { (slice: ArraySlice[Short]) =>
          testShortEncoding(slice, LITTLE_ENDIAN)
        }
      }
      "encoding Int in big-endian" in {
        check { (slice: ArraySlice[Int]) =>
          testIntEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding Int in little-endian" in {
        check { (slice: ArraySlice[Int]) =>
          testIntEncoding(slice, LITTLE_ENDIAN)
        }
      }
      "encoding Long in big-endian" in {
        check { (slice: ArraySlice[Long]) =>
          testLongEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding Long in little-endian" in {
        check { (slice: ArraySlice[Long]) =>
          testLongEncoding(slice, LITTLE_ENDIAN)
        }
      }
      "encoding LongPart in big-endian" in {
        check { (slice: ArrayNumBytes[Long]) =>
          testLongPartEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding LongPart in little-endian" in {
        check { (slice: ArrayNumBytes[Long]) =>
          testLongPartEncoding(slice, LITTLE_ENDIAN)
        }
      }
      "encoding Float in big-endian" in {
        check { (slice: ArraySlice[Float]) =>
          testFloatEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding Float in little-endian" in {
        check { (slice: ArraySlice[Float]) =>
          testFloatEncoding(slice, LITTLE_ENDIAN)
        }
      }
      "encoding Double in big-endian" in {
        check { (slice: ArraySlice[Double]) =>
          testDoubleEncoding(slice, BIG_ENDIAN)
        }
      }
      "encoding Double in little-endian" in {
        check { (slice: ArraySlice[Double]) =>
          testDoubleEncoding(slice, LITTLE_ENDIAN)
        }
      }
    }

    "have correct empty info" when {
      "is empty" in {
        check { (a: ByteStringBuilder) =>
          a.isEmpty
        }
      }
      "is nonEmpty" in {
        check { (a: ByteStringBuilder) =>
          a.putByte(1.toByte)
          a.nonEmpty
        }
      }
    }
  }

  private def makeMultiByteStringsSample(): ByteString = {
    val byteStrings = Vector(
      ByteString1(Array[Byte](0xFF.toByte)),
      ByteString1(Array[Byte](0, 1, 2, 3)),
      ByteString1(Array[Byte](4, 5)),
      ByteString1(Array[Byte](6, 7, 8, 9)),
      ByteString1(Array[Byte](10)),
      ByteString1(Array[Byte](11, 12, 13, 14, 15, 16)),
      ByteString1(Array[Byte](0xFF.toByte))
    )
    ByteStrings(byteStrings)
  }

  private def makeMultiByteStringsWithEmptyComponents(): ByteString = {
    ByteString1(Array.emptyByteArray) ++
    makeMultiByteStringsSample() ++
    ByteString1(Array.emptyByteArray)
  }
}
