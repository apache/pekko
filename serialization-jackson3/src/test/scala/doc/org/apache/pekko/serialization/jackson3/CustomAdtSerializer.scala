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

package doc.org.apache.pekko.serialization.jackson3

object CustomAdtSerializer {

  // #adt-trait-object
  import tools.jackson.core.JsonGenerator
  import tools.jackson.core.JsonParser
  import tools.jackson.databind.DeserializationContext
  import tools.jackson.databind.SerializationContext
  import tools.jackson.databind.annotation.JsonDeserialize
  import tools.jackson.databind.annotation.JsonSerialize
  import tools.jackson.databind.deser.std.StdDeserializer
  import tools.jackson.databind.ser.std.StdSerializer

  @JsonSerialize(`using` = classOf[DirectionValueSerializer])
  @JsonDeserialize(`using` = classOf[DirectionValueDeserializer])
  sealed trait Direction

  object Direction {
    case object North extends Direction
    case object East extends Direction
    case object South extends Direction
    case object West extends Direction
  }

  class DirectionValueSerializer extends StdSerializer[Direction](classOf[Direction]) {
    import Direction._

    override def serialize(value: Direction, gen: JsonGenerator, provider: SerializationContext): Unit = {
      val strValue = value match {
        case North => "N"
        case East  => "E"
        case South => "S"
        case West  => "W"
      }
      gen.writeString(strValue)
    }
  }

  class DirectionValueDeserializer extends StdDeserializer[Direction](classOf[Direction]) {
    import Direction._

    override def deserialize(p: JsonParser, ctxt: DeserializationContext): Direction = {
      p.getString match {
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
