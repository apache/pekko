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

package org.apache.pekko.serialization

import java.nio.ByteBuffer
import java.nio.ByteOrder

import scala.util.Random

import org.apache.pekko
import pekko.testkit.PekkoSpec
import pekko.util.ByteString
import pekko.util.ByteString.ByteString2

import com.typesafe.config.ConfigFactory

object PrimitivesSerializationSpec {
  val serializationTestOverrides = ""

  val testConfig = ConfigFactory.parseString(serializationTestOverrides).withFallback(PekkoSpec.testConf)
}

class PrimitivesSerializationSpec extends PekkoSpec(PrimitivesSerializationSpec.testConfig) {

  val buffer = {
    val b = ByteBuffer.allocate(4096)
    b.order(ByteOrder.LITTLE_ENDIAN)
    b
  }

  val serialization = SerializationExtension(system)

  def verifySerialization(msg: AnyRef): Unit = {
    val serializer = serialization.serializerFor(msg.getClass)
    serializer.fromBinary(serializer.toBinary(msg), None) should ===(msg)
  }

  def verifySerializationByteBuffer(msg: AnyRef): Unit = {
    val serializer = serialization.serializerFor(msg.getClass).asInstanceOf[Serializer with ByteBufferSerializer]
    buffer.clear()
    serializer.toBinary(msg, buffer)
    buffer.flip()

    // also make sure that the Array and ByteBuffer formats are equal, given LITTLE_ENDIAN
    val array1 = new Array[Byte](buffer.remaining())
    buffer.get(array1)
    val array2 = serializer.toBinary(msg)
    ByteString(array1) should ===(ByteString(array2))

    buffer.rewind()
    serializer.fromBinary(buffer, "") should ===(msg)
  }

  def expectByteString2(byteString: ByteString): ByteString2 =
    byteString match {
      case byteString2: ByteString2 => byteString2
      case other                    => fail(s"Expected ByteString2, got [${other.getClass.getName}]")
    }

  "LongSerializer" must {
    Seq(0L, 1L, -1L, Long.MinValue, Long.MinValue + 1L, Long.MaxValue, Long.MaxValue - 1L)
      .map(_.asInstanceOf[AnyRef])
      .foreach { item =>
        s"resolve serializer for value $item" in {
          serialization.serializerFor(item.getClass).getClass should ===(classOf[LongSerializer])
        }

        s"serialize and de-serialize value $item" in {
          verifySerialization(item)
        }

        s"serialize and de-serialize value $item using ByteBuffers" in {
          verifySerializationByteBuffer(item)
        }
      }

    "have right serializer id" in {
      // checking because moved to pekko-actor
      serialization.serializerFor(1L.asInstanceOf[AnyRef].getClass).identifier === 18
    }

  }

  "IntSerializer" must {
    Seq(0, 1, -1, Int.MinValue, Int.MinValue + 1, Int.MaxValue, Int.MaxValue - 1).map(_.asInstanceOf[AnyRef]).foreach {
      item =>
        s"resolve serializer for value $item" in {
          serialization.serializerFor(item.getClass).getClass should ===(classOf[IntSerializer])
        }

        s"serialize and de-serialize value $item" in {
          verifySerialization(item)
        }

        s"serialize and de-serialize value $item using ByteBuffers" in {
          verifySerializationByteBuffer(item)
        }
    }

    "have right serializer id" in {
      // checking because moved to pekko-actor
      serialization.serializerFor(1L.asInstanceOf[AnyRef].getClass).identifier === 19
    }
  }

  "Boolean" must {
    Seq(false, true, java.lang.Boolean.FALSE, java.lang.Boolean.TRUE).map(_.asInstanceOf[AnyRef]).zipWithIndex.foreach {
      case (item, i) =>
        s"resolve serializer for value $item ($i)" in {
          serialization.serializerFor(item.getClass).getClass should ===(classOf[BooleanSerializer])
        }

        s"serialize and de-serialize value $item  ($i)" in {
          verifySerialization(item)
        }

        s"serialize and de-serialize value $item ($i) using ByteBuffers" in {
          verifySerializationByteBuffer(item)
        }
    }

    "have right serializer id  ($i)" in {
      // checking because moved to pekko-actor
      serialization.serializerFor(true.asInstanceOf[AnyRef].getClass).identifier === 35
    }
  }

  "StringSerializer" must {
    val random = Random.nextString(256)
    Seq("empty string" -> "", "hello" -> "hello", "árvíztűrőütvefúrógép" -> "árvíztűrőütvefúrógép", "random" -> random)
      .foreach {
        case (scenario, item) =>
          s"resolve serializer for [$scenario]" in {
            serialization.serializerFor(item.getClass).getClass should ===(classOf[StringSerializer])
          }

          s"serialize and de-serialize [$scenario]" in {
            verifySerialization(item)
          }

          s"serialize and de-serialize value [$scenario] using ByteBuffers" in {
            verifySerializationByteBuffer(item)
          }
      }

    "have right serializer id" in {
      // checking because moved to pekko-actor
      serialization.serializerFor(1L.asInstanceOf[AnyRef].getClass).identifier === 20
    }

  }

  "ByteStringSerializer" must {
    val twoPartByteString =
      expectByteString2(ByteString(Array[Byte](1, 2)) ++ ByteString(Array[Byte](3, 4, 5)))

    Seq(
      "empty string" -> ByteString.empty,
      "simple content" -> ByteString("hello"),
      "two-part content" -> twoPartByteString,
      "concatenated content" -> (ByteString("hello") ++ ByteString("world")),
      "sliced content" -> ByteString("helloabc").take(5),
      "large concatenated" ->
      (ByteString(Array.fill[Byte](1000)(1)) ++ ByteString(Array.fill[Byte](1000)(2)))).foreach {
      case (scenario, item) =>
        s"resolve serializer for [$scenario]" in {
          val serializer = SerializationExtension(system)
          serializer.serializerFor(item.getClass).getClass should ===(classOf[ByteStringSerializer])
        }

        s"serialize and de-serialize [$scenario]" in {
          verifySerialization(item)
        }

        s"serialize and de-serialize value [$scenario] using ByteBuffers" in {
          verifySerializationByteBuffer(item)
        }
    }

    "serialize ByteString2 as contiguous bytes" in {
      val serializer = serialization.serializerFor(twoPartByteString.getClass)

      ByteString(serializer.toBinary(twoPartByteString)) should ===(ByteString(Array[Byte](1, 2, 3, 4, 5)))
    }

    "de-serialize bytes to an equal ByteString" in {
      val serializer = serialization.serializerFor(twoPartByteString.getClass)

      serializer.fromBinary(Array[Byte](1, 2, 3, 4, 5), None) should ===(twoPartByteString)
    }

    "serialize and de-serialize ByteString2 using ByteBuffers" in {
      val serializer =
        serialization.serializerFor(twoPartByteString.getClass).asInstanceOf[Serializer with ByteBufferSerializer]

      buffer.clear()
      serializer.toBinary(twoPartByteString, buffer)
      buffer.flip()

      serializer.fromBinary(buffer, "") should ===(twoPartByteString)
    }

    "have right serializer id" in {
      // checking because moved to pekko-actor
      serialization.serializerFor(1L.asInstanceOf[AnyRef].getClass).identifier === 21
    }

  }

}
