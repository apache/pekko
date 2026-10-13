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

package org.apache.pekko.actor.typed.internal

import java.io.DataInputStream

import scala.collection.mutable

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
 * Guards the `@InternalStableApi` hooks in [[StashBufferImpl]] that instrumentation agents attach bytecode advice to.
 *
 * The hooks only work if `StashBufferImpl` still calls them. The Scala 2 optimizer can inline a small private
 * method into its callers, which leaves the method declared but never invoked, so advice on it silently never
 * runs. This spec reads the compiled class file and checks that each hook is still referenced by a call site.
 */
class StashBufferInstrumentationPointsSpec extends AnyWordSpec with Matchers {

  private val className = "org/apache/pekko/actor/typed/internal/StashBufferImpl"

  private val hooks = List("createNode", "dropHeadForUnstash", "interpretUnstashedMessage", "unstashed", "stashCleared")

  /** `owner` and its anonymous classes, which is where the unstash iteration lives. */
  private def classFiles(owner: String): List[String] =
    owner ::
    Iterator
      .from(1)
      .map(n => s"$owner$$$$anon$$$n")
      .takeWhile(name => getClass.getClassLoader.getResource(name + ".class") ne null)
      .toList

  /** Names of the methods on `owner` that the class file `classFile` invokes. */
  private def invokedMethodNames(classFile: String, owner: String): Set[String] = {
    val in = new DataInputStream(getClass.getClassLoader.getResourceAsStream(classFile + ".class"))
    try {
      in.readInt() // magic
      in.readUnsignedShort() // minor_version
      in.readUnsignedShort() // major_version
      val count = in.readUnsignedShort()
      val utf8 = mutable.Map.empty[Int, String]
      val classes = mutable.Map.empty[Int, Int]
      val nameAndTypes = mutable.Map.empty[Int, Int]
      val methodRefs = mutable.ListBuffer.empty[(Int, Int)]
      var i = 1
      while (i < count) {
        in.readUnsignedByte() match {
          case 1       => utf8(i) = in.readUTF()
          case 7       => classes(i) = in.readUnsignedShort()
          case 10 | 11 =>
            methodRefs += ((in.readUnsignedShort(), in.readUnsignedShort()))
          case 12 =>
            nameAndTypes(i) = in.readUnsignedShort()
            in.readUnsignedShort()
          case 3 | 4 | 9 | 17 | 18 => in.readInt()
          case 5 | 6               =>
            in.readLong()
            i += 1 // long and double entries take two slots
          case 8 | 16 | 19 | 20 => in.readUnsignedShort()
          case 15               =>
            in.readUnsignedByte()
            in.readUnsignedShort()
          case tag => fail(s"Unexpected constant pool tag $tag in $classFile")
        }
        i += 1
      }
      methodRefs.collect {
        case (classIndex, natIndex) if utf8(classes(classIndex)) == owner => utf8(nameAndTypes(natIndex))
      }.toSet
    } finally in.close()
  }

  "StashBufferImpl" should {

    "still call its @InternalStableApi instrumentation hooks" in {
      val files = classFiles(className)
      files.size should be > 1
      val invoked = files.flatMap(invokedMethodNames(_, className)).toSet
      // a hook accessed from a closure can get an expanded name such as `...StashBufferImpl$$dropHeadForUnstash`
      val notInvoked = hooks.filterNot(hook => invoked.exists(name => name == hook || name.endsWith("$$" + hook)))
      notInvoked shouldBe empty
    }
  }
}
