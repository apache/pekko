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

package org.apache.pekko.stream.impl.fusing

import scala.concurrent.{ Future, Promise }

import org.apache.pekko
import pekko.annotation.InternalApi
import pekko.stream.{ AbruptStageTerminationException, Attributes, Inlet, SinkShape }
import pekko.stream.impl.Stages.DefaultAttributes
import pekko.stream.stage.{ GraphStageLogic, GraphStageWithMaterializedValue, InHandler }

/**
 * INTERNAL API
 */
@InternalApi private[pekko] object CountSink
    extends GraphStageWithMaterializedValue[SinkShape[Any], Future[Long]] {
  private val in = Inlet[Any]("seq.in")
  override def shape: SinkShape[Any] = SinkShape.of(in)
  override def toString: String = "CountSink"
  override protected def initialAttributes: Attributes = DefaultAttributes.countSink

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Long]) = {
    val promise = Promise[Long]()
    object logic extends GraphStageLogic(shape) with InHandler {
      private var counter: Long = 0L
      override def preStart(): Unit = pull(in)
      override def onPush(): Unit = {
        counter += 1
        pull(in)
      }
      override def onUpstreamFinish(): Unit = {
        promise.trySuccess(counter)
        completeStage()
      }

      override def onUpstreamFailure(ex: Throwable): Unit = {
        promise.tryFailure(ex)
        failStage(ex)
      }

      override def postStop(): Unit = {
        if (!promise.isCompleted) promise.failure(new AbruptStageTerminationException(this))
      }

      setHandler(in, this)
    }

    (logic, promise.future)
  }
}
