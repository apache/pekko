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

package org.apache.pekko.cluster.metrics.protobuf

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream }
import java.math.BigInteger
import scala.math.BigInt

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object NumberInputStreamSpec {
  case class TestObject(value: String) extends Serializable
}

class NumberInputStreamSpec extends AnyWordSpec with Matchers {

  import NumberInputStreamSpec._
  val classLoader = classOf[NumberInputStreamSpec].getClassLoader

  "NumberInputStream" must {

    "resolve java Integer" in {

      val i = 42
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(Integer.valueOf(i))
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe an[Integer]
      result.asInstanceOf[Integer].intValue() shouldEqual i
    }

    "resolve scala Int" in {

      val i = 42
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(i)
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe an[Int]
      result.asInstanceOf[Int] shouldEqual i
    }

    "resolve java BigInteger" in {

      val i = BigInteger.valueOf(Long.MaxValue)
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(i)
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe a[BigInteger]
      result.asInstanceOf[BigInteger] shouldEqual i
    }

    "resolve scala BigInt" in {

      val i = BigInt(Long.MaxValue).+(BigInt(1))
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(i)
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe a[BigInt]
      result.asInstanceOf[BigInt] shouldEqual i
    }

    "resolve java BigDecimal" in {

      val n = new java.math.BigDecimal("123456789012345678901234567890.12345678901234567890")
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(n)
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe a[java.math.BigDecimal]
      result.asInstanceOf[java.math.BigDecimal] shouldEqual n
    }

    "resolve scala BigDecimal" in {

      val n = scala.math.BigDecimal("123456789012345678901234567890.12345678901234567890")
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(n)
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      val result = numberInputStream.readObject()
      numberInputStream.close()
      result shouldBe a[scala.math.BigDecimal]
      result.asInstanceOf[scala.math.BigDecimal] shouldEqual n
    }

    "throw ClassNotFoundException for non-Number classes" in {
      val bos = new ByteArrayOutputStream()
      val oos = new ObjectOutputStream(bos)
      oos.writeObject(TestObject("NotANumber"))
      oos.close()

      val inputStream = new ByteArrayInputStream(bos.toByteArray)
      val numberInputStream = new NumberInputStream(classLoader, inputStream)

      a[ClassNotFoundException] should be thrownBy {
        numberInputStream.readObject()
      }
      numberInputStream.close()
    }
  }
}
