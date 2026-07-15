/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2015-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.scaladsl

import scala.util.control.NonFatal

import org.apache.pekko

import pekko.NotUsed
import pekko.stream.Attributes
import pekko.stream.impl.JsonObjectParser
import pekko.stream.impl.fusing.GraphStages.SimpleLinearGraphStage
import pekko.stream.scaladsl.Framing.FramingException
import pekko.stream.stage.{ GraphStageLogic, InHandler, OutHandler }
import pekko.util.ByteString

/** Provides JSON framing operators that can separate JSON objects from incoming [[pekko.util.ByteString]] objects. */
object JsonFraming {

  /** Thrown if upstream completes with a partial object in the buffer. */
  class PartialObjectException(msg: String = "JSON stream completed with partial content in the buffer!")
      extends FramingException(msg)

  /**
   * Returns a Flow that implements a "brace counting" based framing operator for emitting JSON objects.
   * It scans the incoming data stream for JSON objects and returns chunks of ByteStrings containing those objects.
   *
   * This operator only frames JSON objects; it is not a general-purpose JSON parser and does not validate the
   * object contents. A top-level JSON array is supported only when all its elements are objects. Other top-level JSON
   * values, such as strings, numbers, booleans, or `null`, are not supported. JSON Lines or NDJSON input is supported
   * when every record is an object. A leading UTF-8 byte-order mark (BOM) must be removed before framing.
   *
   * Typical examples of data that one may want to frame using this operator include:
   *
   * **Very large arrays**:
   * {{{
   *   [{"id": 1}, {"id": 2}, [...], {"id": 999}]
   * }}}
   *
   * **Multiple concatenated JSON objects** (with, or without commas between them):
   *
   * {{{
   *   {"id": 1}, {"id": 2}, [...], {"id": 999}
   * }}}
   *
   * The framing works independently of formatting, i.e. it will still emit JSON objects even if two
   * elements are separated by multiple newlines or other whitespace characters. And of course is insensitive
   * (and does not impact the emitting frame) to the JSON object's internal formatting.
   *
   * For streaming nested JSON structures, see
   * <a href="https://pekko.apache.org/docs/pekko-connectors/current/data-transformations/json.html">Apache Pekko Connectors JSON support</a>.
   *
   * If the stream completes while mid-object, the stage will fail with a [[PartialObjectException]].
   *
   * @param maximumObjectLength The maximum length of allowed frames while decoding. If the maximum length is exceeded
   *                            this Flow will fail the stream.
   */
  def objectScanner(maximumObjectLength: Int): Flow[ByteString, ByteString, NotUsed] =
    Flow[ByteString].via(new SimpleLinearGraphStage[ByteString] {

      override protected def initialAttributes: Attributes = Attributes.name("JsonFraming.objectScanner")

      override def createLogic(inheritedAttributes: Attributes) =
        new GraphStageLogic(shape) with InHandler with OutHandler {
          private val buffer = new JsonObjectParser(maximumObjectLength)

          setHandlers(in, out, this)

          override def onPush(): Unit = {
            buffer.offer(grab(in))
            tryPopBuffer()
          }

          override def onPull(): Unit =
            tryPopBuffer()

          override def onUpstreamFinish(): Unit = {
            buffer.poll() match {
              case Some(json) => emit(out, json)
              case _          => complete()
            }
          }

          def tryPopBuffer(): Unit = {
            try buffer.poll() match {
                case Some(json) => push(out, json)
                case _          => if (isClosed(in)) complete() else pull(in)
              }
            catch {
              case NonFatal(ex) => failStage(ex)
            }
          }

          def complete(): Unit =
            if (buffer.canComplete) completeStage()
            else failStage(new PartialObjectException)
        }
    })

}
