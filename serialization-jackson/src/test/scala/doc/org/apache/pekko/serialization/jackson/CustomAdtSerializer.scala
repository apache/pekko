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

object CustomAdtSerializer {

  // #adt-trait-object
  import com.fasterxml.jackson.core.{ JsonGenerator, JsonParser }
  import com.fasterxml.jackson.databind.{ DeserializationContext, SerializerProvider }
  import com.fasterxml.jackson.databind.annotation.{ JsonDeserialize, JsonSerialize }
  import com.fasterxml.jackson.databind.deser.std.StdDeserializer
  import com.fasterxml.jackson.databind.ser.std.StdSerializer

  @JsonSerialize(`using` = classOf[DirectionJsonSerializer])
  @JsonDeserialize(`using` = classOf[DirectionJsonDeserializer])
  sealed trait Direction

  object Direction {
    case object North extends Direction
    case object East extends Direction
    case object South extends Direction
    case object West extends Direction
  }

  class DirectionJsonSerializer extends StdSerializer[Direction](classOf[Direction]) {
    import Direction._

    override def serialize(value: Direction, gen: JsonGenerator, provider: SerializerProvider): Unit = {
      val strValue = value match {
        case North => "N"
        case East  => "E"
        case South => "S"
        case West  => "W"
      }
      gen.writeString(strValue)
    }
  }

  class DirectionJsonDeserializer extends StdDeserializer[Direction](classOf[Direction]) {
    import Direction._

    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Direction = {
      p.getText match {
        case "N" => North
        case "E" => East
        case "S" => South
        case "W" => West
      }
    }
  }

  final case class Compass(currentDirection: Direction) extends MySerializable
  // #adt-trait-object
}
