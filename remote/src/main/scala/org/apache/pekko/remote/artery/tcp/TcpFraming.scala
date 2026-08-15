/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2018-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.remote.artery
package tcp

import java.nio.ByteBuffer
import java.nio.ByteOrder

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.Attributes
import pekko.stream.impl.io.ByteStringParser
import pekko.stream.impl.io.ByteStringParser.ByteReader
import pekko.stream.impl.io.ByteStringParser.ParseResult
import pekko.stream.impl.io.ByteStringParser.ParseStep
import pekko.stream.scaladsl.Framing.FramingException
import pekko.stream.stage.GraphStageLogic
import pekko.util.ByteString

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object TcpFraming {
  val Undefined = Int.MinValue

  /**
   * The legacy 4-byte magic header from Akka (AKKA).
   * The purpose of the "magic" is to detect and reject weird (accidental) accesses.
   */
  val DefaultMagic = ByteString('A'.toByte, 'K'.toByte, 'K'.toByte, 'A'.toByte)

  /**
   * The default 4-byte magic header for Pekko 2.x (PEKK).
   * The purpose of the "magic" is to detect and reject weird (accidental) accesses.
   */
  val PekkoMagic = ByteString('P'.toByte, 'E'.toByte, 'K'.toByte, 'K'.toByte)

  /**
   * When establishing the connection this header is sent first.
   * It contains a "magic" and the stream identifier for selecting control, ordinary, large
   * inbound streams.
   *
   * The purpose of the "magic" is to detect and reject weird (accidental) accesses.
   * The magic 4 bytes are configurable via `pekko.remote.artery.advanced.tcp-magic`.
   *
   * The `streamId` is encoded as 1 byte.
   */
  def encodeConnectionHeader(magic: ByteString, streamId: Int): ByteString =
    magic ++ ByteString.fromArrayUnsafe(Array(streamId.toByte))

  /**
   * Each frame starts with the frame header that contains the length
   * of the frame. The `frameLength` is encoded as 4 bytes (little endian).
   */
  def encodeFrameHeader(frameLength: Int): ByteString =
    ByteString.fromArrayUnsafe(
      Array[Byte](
        (frameLength & 0xFF).toByte,
        ((frameLength & 0xFF00) >> 8).toByte,
        ((frameLength & 0xFF0000) >> 16).toByte,
        ((frameLength & 0xFF000000) >> 24).toByte))
}

/**
 * INTERNAL API
 */
@InternalApi private[pekko] class TcpFraming(
    acceptedMagic: Set[ByteString] = Set(TcpFraming.DefaultMagic),
    flightRecorder: RemotingFlightRecorder = NoOpRemotingFlightRecorder)
    extends ByteStringParser[EnvelopeBuffer] {

  private val magicLength = acceptedMagic.head.length

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new ParsingLogic {

    abstract class Step extends ParseStep[EnvelopeBuffer]
    startWith(ReadMagic)

    case object ReadMagic extends Step {
      override def parse(reader: ByteReader): ParseResult[EnvelopeBuffer] = {
        val receivedMagic = reader.take(magicLength)
        if (acceptedMagic.contains(receivedMagic))
          ParseResult(None, ReadStreamId)
        else
          throw new FramingException(
            "Stream didn't start with expected magic bytes, " +
            s"got [${(receivedMagic ++ reader.remainingData).take(10).map("%02x".format(_)).mkString(" ")}] " +
            "Connection is rejected. Probably invalid accidental access.")
      }
    }
    case object ReadStreamId extends Step {
      override def parse(reader: ByteReader): ParseResult[EnvelopeBuffer] =
        ParseResult(None, ReadFrame(reader.readByte()))
    }
    case class ReadFrame(streamId: Int) extends Step {
      override def onTruncation(): Unit =
        failStage(new FramingException("Stream finished but there was a truncated final frame in the buffer"))

      override def parse(reader: ByteReader): ParseResult[EnvelopeBuffer] = {
        val frameLength = reader.readIntLE()
        val buffer = createBuffer(reader.take(frameLength))
        ParseResult(Some(buffer), this)
      }

      private def createBuffer(bs: ByteString): EnvelopeBuffer = {
        val buffer = ByteBuffer.wrap(bs.toArray)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        flightRecorder.tcpInboundReceived(buffer.limit)
        val res = new EnvelopeBuffer(buffer)
        res.setStreamId(streamId)
        res
      }
    }
  }
}
