/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

/*
 * Copyright (C) 2014-2022 Lightbend Inc. <https://www.lightbend.com>
 */

package org.apache.pekko.stream.tck

import java.nio.file.Files

import org.testng.annotations.{ AfterClass, BeforeClass }

import org.apache.pekko
import pekko.actor.ActorSystem
import pekko.event.Logging
import pekko.stream.scaladsl.{ FileIO, Sink }
import pekko.stream.testkit.Utils._
import pekko.testkit.{ EventFilter, TestEvent }
import pekko.testkit.PekkoSpec
import pekko.util.ByteString

import org.reactivestreams.Publisher

class FilePublisherTest extends PekkoPublisherVerification[ByteString] {

  val ChunkSize = 256
  val Elements = 1000

  @BeforeClass
  override def createActorSystem(): Unit = {
    _system = ActorSystem(Logging.simpleName(getClass), UnboundedMailboxConfig.withFallback(PekkoSpec.testConf))
    _system.eventStream.publish(TestEvent.Mute(EventFilter[RuntimeException]("Test exception")))
  }

  val file = {
    val f = Files.createTempFile("file-source-tck", ".tmp")
    val chunk = "x" * ChunkSize

    val fw = Files.newBufferedWriter(f)
    List.fill(Elements)(chunk).foreach(fw.append)
    fw.close()
    f
  }

  def createPublisher(elements: Long): Publisher[ByteString] =
    FileIO.fromPath(file, chunkSize = 512).take(elements).runWith(Sink.asPublisher(false))

  @AfterClass
  def after() = Files.delete(file)

  override def maxElementsFromPublisher(): Long = Elements
}
