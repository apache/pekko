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

package doc.org.apache.pekko.serialization.jackson

import java.util.Optional

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.serialization.Serialization
import pekko.serialization.SerializationExtension
import pekko.serialization.SerializerWithStringManifest
import pekko.serialization.Serializers
import pekko.testkit.TestKit
import com.fasterxml.jackson.annotation.{ JsonSubTypes, JsonTypeInfo, JsonTypeName }
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

//#marker-interface
/**
 * Marker interface for messages, events and snapshots that are serialized with Jackson.
 */
trait MySerializable

final case class Message(name: String, nr: Int) extends MySerializable
//#marker-interface

object SerializationDocSpec {
  val config = """
    #//#serialization-bindings
    pekko.actor {
      serialization-bindings {
        "com.myservice.MySerializable" = jackson-json
      }
    }
    #//#serialization-bindings
  """

  val configMigration = """
    #//#migrations-conf
    pekko.serialization.jackson.migrations {
      "com.myservice.event.ItemAdded" = "com.myservice.event.ItemAddedMigration"
    }
    #//#migrations-conf
  """

  val configMigrationRenamClass = """
    #//#migrations-conf-rename
    pekko.serialization.jackson.migrations {
      "com.myservice.event.OrderAdded" = "com.myservice.event.OrderPlacedMigration"
    }
    #//#migrations-conf-rename
  """

  val configSpecific = """
    #//#specific-config
    pekko.serialization.jackson.jackson-json {
      serialization-features {
        WRITE_DATES_AS_TIMESTAMPS = off
      }
    }
    pekko.serialization.jackson.jackson-cbor {
      serialization-features {
        WRITE_DATES_AS_TIMESTAMPS = on
      }
    }
    #//#specific-config
  """

  val configSeveral = """
    #//#several-config
    pekko.actor {
      serializers {
        jackson-json-message = "org.apache.pekko.serialization.jackson.JacksonJsonSerializer"
        jackson-json-event   = "org.apache.pekko.serialization.jackson.JacksonJsonSerializer"
      }
      serialization-identifiers {
        jackson-json-message = 9001
        jackson-json-event = 9002
      }
      serialization-bindings {
        "com.myservice.MyMessage" = jackson-json-message
        "com.myservice.MyEvent" = jackson-json-event
      }
    }
    pekko.serialization.jackson {
      jackson-json-message {
        serialization-features {
          WRITE_DATES_AS_TIMESTAMPS = on
        }
      }
      jackson-json-event {
        serialization-features {
          WRITE_DATES_AS_TIMESTAMPS = off
        }
      }
    }
    #//#several-config
  """

  val configManifestless = """
    #//#manifestless
    pekko.actor {
      serializers {
        jackson-json-event = "org.apache.pekko.serialization.jackson.JacksonJsonSerializer"
      }
      serialization-identifiers {
        jackson-json-event = 9001
      }
      serialization-bindings {
        "com.myservice.MyEvent" = jackson-json-event
      }
    }
    pekko.serialization.jackson {
      jackson-json-event {
        type-in-manifest = off
        # Since there is exactly one serialization binding declared for this
        # serializer above, this is optional, but if there were none or many,
        # this would be mandatory.
        deserialization-type = "com.myservice.MyEvent"
      }
    }
    #//#manifestless
  """

  object Polymorphism {

    // #polymorphism
    final case class Zoo(primaryAttraction: Animal) extends MySerializable

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(value = classOf[Lion], name = "lion"),
        new JsonSubTypes.Type(value = classOf[Elephant], name = "elephant")))
    sealed trait Animal

    final case class Lion(name: String) extends Animal

    final case class Elephant(name: String, age: Int) extends Animal
    // #polymorphism
  }

  object PolymorphismMixedClassObject {

    // #polymorphism-case-object
    final case class Zoo(primaryAttraction: Animal) extends MySerializable

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
      Array(
        new JsonSubTypes.Type(value = classOf[Lion], name = "lion"),
        new JsonSubTypes.Type(value = classOf[Elephant], name = "elephant"),
        new JsonSubTypes.Type(value = classOf[Unicorn], name = "unicorn")))
    sealed trait Animal

    final case class Lion(name: String) extends Animal
    final case class Elephant(name: String, age: Int) extends Animal

    @JsonDeserialize(`using` = classOf[UnicornDeserializer])
    sealed trait Unicorn extends Animal
    @JsonTypeName("unicorn")
    case object Unicorn extends Unicorn

    class UnicornDeserializer extends StdDeserializer[Unicorn](Unicorn.getClass) {
      // whenever we need to deserialize an instance of Unicorn trait, we return the object Unicorn
      override def deserialize(p: JsonParser, ctxt: DeserializationContext): Unicorn = Unicorn
    }
    // #polymorphism-case-object
  }

  val configDateTime = """
    #//#date-time
    pekko.serialization.jackson.serialization-features {
      WRITE_DATES_AS_TIMESTAMPS = on
      WRITE_DURATIONS_AS_TIMESTAMPS = on
    }
    #//#date-time
    """

  val configAllowList = """
    #//#allowed-class-prefix
    pekko.serialization.jackson.allowed-class-prefix =
      ["com.myservice.event.OrderAdded", "com.myservice.command"]
    #//#allowed-class-prefix
  """

}

class SerializationDocSpec
    extends TestKit(
      ActorSystem(
        "SerializationDocSpec",
        ConfigFactory.parseString(s"""
    pekko.serialization.jackson.migrations {
        # migrations for Java classes
        "jdoc.org.apache.pekko.serialization.jackson.v2b.ItemAdded" = "jdoc.org.apache.pekko.serialization.jackson.v2b.ItemAddedMigration"
        "jdoc.org.apache.pekko.serialization.jackson.v2c.ItemAdded" = "jdoc.org.apache.pekko.serialization.jackson.v2c.ItemAddedMigration"
        "jdoc.org.apache.pekko.serialization.jackson.v2a.Customer" = "jdoc.org.apache.pekko.serialization.jackson.v2a.CustomerMigration"
        "jdoc.org.apache.pekko.serialization.jackson.v1.OrderAdded" = "jdoc.org.apache.pekko.serialization.jackson.v2a.OrderPlacedMigration"

        # migrations for Scala classes
        "doc.org.apache.pekko.serialization.jackson.v2b.ItemAdded" = "doc.org.apache.pekko.serialization.jackson.v2b.ItemAddedMigration"
        "doc.org.apache.pekko.serialization.jackson.v2c.ItemAdded" = "doc.org.apache.pekko.serialization.jackson.v2c.ItemAddedMigration"
        "doc.org.apache.pekko.serialization.jackson.v2a.Customer" = "doc.org.apache.pekko.serialization.jackson.v2a.CustomerMigration"
        "doc.org.apache.pekko.serialization.jackson.v1.OrderAdded" = "doc.org.apache.pekko.serialization.jackson.v2a.OrderPlacedMigration"
    }
    pekko.actor {
      allow-java-serialization = off
      serialization-bindings {
        "${classOf[jdoc.org.apache.pekko.serialization.jackson.MySerializable].getName}" = jackson-json
        "${classOf[doc.org.apache.pekko.serialization.jackson.MySerializable].getName}" = jackson-json
      }
    }
    """)))
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  val serialization: Serialization = SerializationExtension(system)

  override def afterAll(): Unit =
    shutdown()

  def verifySerialization(obj: AnyRef): AnyRef = {
    val serializer = serialization.serializerFor(obj.getClass)
    val manifest = Serializers.manifestFor(serializer, obj)
    val serializerId = serializer.identifier
    val blob = serialization.serialize(obj).get
    val deserialized = serialization.deserialize(blob, serializerId, manifest).get
    deserialized
  }

  private def serializerFor(obj: Any): SerializerWithStringManifest =
    serialization.serializerFor(obj.getClass).asInstanceOf[SerializerWithStringManifest]

  "serialize trait + case classes" in {
    import doc.org.apache.pekko.serialization.jackson.SerializationDocSpec.Polymorphism._
    verifySerialization(Zoo(Lion("Simba"))) should ===(Zoo(Lion("Simba")))
    verifySerialization(Zoo(Elephant("Dumbo", 1))) should ===(Zoo(Elephant("Dumbo", 1)))
  }

  "serialize trait + case classes + case object" in {
    import doc.org.apache.pekko.serialization.jackson.SerializationDocSpec.PolymorphismMixedClassObject._
    verifySerialization(Zoo(Lion("Simba"))) should ===(Zoo(Lion("Simba")))
    verifySerialization(Zoo(Elephant("Dumbo", 1))) should ===(Zoo(Elephant("Dumbo", 1)))
    verifySerialization(Zoo(Unicorn)) should ===(Zoo(Unicorn))
  }

  "serialize trait + object ADT" in {
    import CustomAdtSerializer.Compass
    import CustomAdtSerializer.Direction._

    verifySerialization(Compass(North)) should ===(Compass(North))
    verifySerialization(Compass(East)) should ===(Compass(East))
    verifySerialization(Compass(South)) should ===(Compass(South))
    verifySerialization(Compass(West)) should ===(Compass(West))
  }

  "EventMigration doc sample Java classes" must {
    import jdoc.org.apache.pekko.serialization.jackson.v1.OrderAdded
    import jdoc.org.apache.pekko.serialization.jackson.v2a.Customer
    import jdoc.org.apache.pekko.serialization.jackson.v2a.ItemAdded
    import jdoc.org.apache.pekko.serialization.jackson.v2a.OrderPlaced

    "test add optional field" in {
      val event1 =
        new jdoc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer.fromBinary(blob, classOf[ItemAdded].getName).asInstanceOf[ItemAdded]
      event2.quantity should ===(event1.quantity)
      event2.discount should ===(Optional.empty())
      event2.note should ===("")

      verifySerialization(new ItemAdded("123", "ab123", 2, Optional.of(0.1), "thanks"))
    }

    "test add mandatory field" in {
      val event1 =
        new jdoc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer
        .fromBinary(blob, classOf[jdoc.org.apache.pekko.serialization.jackson.v2b.ItemAdded].getName)
        .asInstanceOf[jdoc.org.apache.pekko.serialization.jackson.v2b.ItemAdded]
      event2.quantity should ===(event1.quantity)
      event2.discount should be(0.0 +- 0.000001)

      verifySerialization(new jdoc.org.apache.pekko.serialization.jackson.v2b.ItemAdded("123", "ab123", 2, 0.1))
    }

    "test rename field" in {
      val event1 =
        new jdoc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer
        .fromBinary(blob, classOf[jdoc.org.apache.pekko.serialization.jackson.v2c.ItemAdded].getName)
        .asInstanceOf[jdoc.org.apache.pekko.serialization.jackson.v2c.ItemAdded]
      event2.itemId should ===(event1.productId)
      event2.quantity should ===(event1.quantity)
    }

    "test structural changes" in {
      val cust1 =
        new jdoc.org.apache.pekko.serialization.jackson.v1.Customer("A", "B", "C", "D", "E")
      val serializer = serializerFor(cust1)
      val blob = serializer.toBinary(cust1)
      val cust2 = serializer.fromBinary(blob, classOf[Customer].getName).asInstanceOf[Customer]
      cust2.name should ===(cust1.name)
      cust2.shippingAddress.street should ===(cust1.street)
      cust2.shippingAddress.city should ===(cust1.city)
      cust2.shippingAddress.zipCode should ===(cust1.zipCode)
      cust2.shippingAddress.country should ===(cust1.country)
    }

    "test rename class" in {
      val order1 = new OrderAdded("1234")
      val serializer = serializerFor(order1)
      val blob = serializer.toBinary(order1)
      val order2 = serializer.fromBinary(blob, classOf[OrderAdded].getName).asInstanceOf[OrderPlaced]
      order2.shoppingCartId should ===(order1.shoppingCartId)
    }
  }

  "EventMigration doc sample Scala classes" must {
    import doc.org.apache.pekko.serialization.jackson.v1.OrderAdded
    import doc.org.apache.pekko.serialization.jackson.v2a.Customer
    import doc.org.apache.pekko.serialization.jackson.v2a.ItemAdded
    import doc.org.apache.pekko.serialization.jackson.v2a.OrderPlaced

    "test add optional field" in {
      val event1 =
        doc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer.fromBinary(blob, classOf[ItemAdded].getName).asInstanceOf[ItemAdded]
      event2.quantity should ===(event1.quantity)
      event2.discount should ===(None)
      event2.note should ===("")

      verifySerialization(ItemAdded("123", "ab123", 2, Some(0.1), "thanks"))
    }

    "test add mandatory field" in {
      val event1 =
        doc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer
        .fromBinary(blob, classOf[doc.org.apache.pekko.serialization.jackson.v2b.ItemAdded].getName)
        .asInstanceOf[doc.org.apache.pekko.serialization.jackson.v2b.ItemAdded]
      event2.quantity should ===(event1.quantity)
      event2.discount should be(0.0 +- 0.000001)

      verifySerialization(doc.org.apache.pekko.serialization.jackson.v2b.ItemAdded("123", "ab123", 2, 0.1))
    }

    "test rename field" in {
      val event1 =
        doc.org.apache.pekko.serialization.jackson.v1.ItemAdded("123", "ab123", 2)
      val serializer = serializerFor(event1)
      val blob = serializer.toBinary(event1)
      val event2 = serializer
        .fromBinary(blob, classOf[doc.org.apache.pekko.serialization.jackson.v2c.ItemAdded].getName)
        .asInstanceOf[doc.org.apache.pekko.serialization.jackson.v2c.ItemAdded]
      event2.itemId should ===(event1.productId)
      event2.quantity should ===(event1.quantity)
    }

    "test structural changes" in {
      val cust1 =
        doc.org.apache.pekko.serialization.jackson.v1.Customer("A", "B", "C", "D", "E")
      val serializer = serializerFor(cust1)
      val blob = serializer.toBinary(cust1)
      val cust2 = serializer.fromBinary(blob, classOf[Customer].getName).asInstanceOf[Customer]
      cust2.name should ===(cust1.name)
      cust2.shippingAddress.street should ===(cust1.street)
      cust2.shippingAddress.city should ===(cust1.city)
      cust2.shippingAddress.zipCode should ===(cust1.zipCode)
      cust2.shippingAddress.country should ===(cust1.country)
    }

    "test rename class" in {
      val order1 = OrderAdded("1234")
      val serializer = serializerFor(order1)
      val blob = serializer.toBinary(order1)
      val order2 = serializer.fromBinary(blob, classOf[OrderAdded].getName).asInstanceOf[OrderPlaced]
      order2.shoppingCartId should ===(order1.shoppingCartId)
    }
  }

}
