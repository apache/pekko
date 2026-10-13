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

import java.lang.{ Double => JDouble, Float => JFloat }

/**
 * Set of methods which allow easy implementation of <code>hashCode</code>.
 *
 * Example:
 * <pre>
 *  override def hashCode: Int = {
 *    var result = HashCode.SEED
 *    //collect the contributions of various fields
 *    result = HashCode.hash(result, fPrimitive)
 *    result = HashCode.hash(result, fObject)
 *    result = HashCode.hash(result, fArray)
 *    result
 *  }
 * </pre>
 */
object HashCode {
  val SEED = 23

  def hash(seed: Int, any: Any): Int = any match {
    case value: Boolean => hash(seed, value)
    case value: Char    => hash(seed, value)
    case value: Short   => hash(seed, value)
    case value: Int     => hash(seed, value)
    case value: Long    => hash(seed, value)
    case value: Float   => hash(seed, value)
    case value: Double  => hash(seed, value)
    case value: Byte    => hash(seed, value)
    case value: AnyRef  =>
      var result = seed
      if (value eq null) result = hash(result, 0)
      else if (!isArray(value)) result = hash(result, value.hashCode())
      else result = hashArray(result, value)
      result
    case unexpected =>
      throw new IllegalArgumentException(s"Unexpected hash parameter: $unexpected") // will not happen, for exhaustiveness check
  }
  def hash(seed: Int, value: Boolean): Int = firstTerm(seed) + (if (value) 1 else 0)
  def hash(seed: Int, value: Char): Int = firstTerm(seed) + value.asInstanceOf[Int]
  def hash(seed: Int, value: Int): Int = firstTerm(seed) + value
  def hash(seed: Int, value: Long): Int = firstTerm(seed) + (value ^ (value >>> 32)).asInstanceOf[Int]
  def hash(seed: Int, value: Float): Int = hash(seed, JFloat.floatToIntBits(value))
  def hash(seed: Int, value: Double): Int = hash(seed, JDouble.doubleToLongBits(value))

  private def firstTerm(seed: Int): Int = PRIME * seed
  private def isArray(anyRef: AnyRef): Boolean = anyRef.getClass.isArray
  private def hashArray(seed: Int, value: AnyRef): Int = {
    var result = seed
    value match {
      case array: Array[AnyRef] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Boolean] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Char] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Short] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Int] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Long] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Float] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Double] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case array: Array[Byte] =>
        var index = 0
        while (index < array.length) {
          result = hash(result, array(index))
          index += 1
        }
      case unexpected =>
        throw new IllegalArgumentException(s"Unexpected array hash parameter: $unexpected")
    }
    result
  }
  private val PRIME = 37
}
