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

package org.apache.pekko.persistence.state.exception

import java.lang.invoke.{ MethodHandles, MethodType }

import scala.util.Try
import scala.util.control.NoStackTrace

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers

/**
 * Tests for [[DurableStateException]]s.
 * <p>
 *   To avoid making Pekko persistence implementations dependent on
 *   Pekko v1.1, these exceptions will be created using MethodHandles.
 * </p>
 */
class DurableStateExceptionsSpec extends AnyWordSpecLike
    with Matchers {

  private val methodHandleLookup = MethodHandles.publicLookup()

  "DurableStateException support" must {
    "allow creating OutOfDateRevisionException using MethodHandle" in {
      val outOfDateRevisionExceptionClassOpt: Option[Class[_]] = Try(Class.forName(
        "org.apache.pekko.persistence.state.exception.OutOfDateRevisionException")).toOption
      outOfDateRevisionExceptionClassOpt should not be empty
      val constructorOpt = outOfDateRevisionExceptionClassOpt.map { clz =>
        val mt = MethodType.methodType(classOf[Unit], classOf[String])
        methodHandleLookup.findConstructor(clz, mt)
      }
      constructorOpt should not be empty
      val constructor = constructorOpt.get
      val ex = constructor.invoke("message").asInstanceOf[Exception]
      ex shouldBe an[OutOfDateRevisionException]
      ex shouldBe an[NoStackTrace]
      ex.getMessage shouldEqual "message"
    }
    "allow creating UnknownRevisionException using MethodHandle" in {
      val exceptionClassOpt: Option[Class[_]] = Try(Class.forName(
        "org.apache.pekko.persistence.state.exception.UnknownRevisionException")).toOption
      exceptionClassOpt should not be empty
      val constructorOpt = exceptionClassOpt.map { clz =>
        val mt = MethodType.methodType(classOf[Unit], classOf[String])
        methodHandleLookup.findConstructor(clz, mt)
      }
      constructorOpt should not be empty
      val constructor = constructorOpt.get
      val ex = constructor.invoke("message").asInstanceOf[Exception]
      ex shouldBe an[UnknownRevisionException]
      ex shouldBe an[NoStackTrace]
      ex.getMessage shouldEqual "message"
    }
  }
}
