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

package org.apache.pekko.stream

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import scala.annotation.nowarn

import org.apache.pekko

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

object DslConsistencySpec {
  class ScalaSubSource[Out, Mat]
      extends impl.SubFlowImpl[Out, Out, Mat, scaladsl.Source[Out, Mat]#Repr, scaladsl.RunnableGraph[Mat]](
        null,
        null,
        null)
  class ScalaSubFlow[In, Out, Mat]
      extends impl.SubFlowImpl[Out, Out, Mat, scaladsl.Flow[In, Out, Mat]#Repr, scaladsl.Sink[In, Mat]](
        null,
        null,
        null)
}

class DslConsistencySpec extends AnyWordSpec with Matchers {

  val sFlowClass: Class[?] = classOf[pekko.stream.scaladsl.Flow[?, ?, ?]]
  val jFlowClass: Class[?] = classOf[pekko.stream.javadsl.Flow[?, ?, ?]]

  val sSubFlowClass: Class[?] = classOf[DslConsistencySpec.ScalaSubFlow[?, ?, ?]]
  val jSubFlowClass: Class[?] = classOf[pekko.stream.javadsl.SubFlow[?, ?, ?]]

  val sSourceClass: Class[?] = classOf[pekko.stream.scaladsl.Source[?, ?]]
  val jSourceClass: Class[?] = classOf[pekko.stream.javadsl.Source[?, ?]]

  val sSubSourceClass: Class[?] = classOf[DslConsistencySpec.ScalaSubSource[?, ?]]
  val jSubSourceClass: Class[?] = classOf[pekko.stream.javadsl.SubSource[?, ?]]

  val sSinkClass: Class[?] = classOf[pekko.stream.scaladsl.Sink[?, ?]]
  val jSinkClass: Class[?] = classOf[pekko.stream.javadsl.Sink[?, ?]]

  val jRunnableGraphClass: Class[?] = classOf[pekko.stream.javadsl.RunnableGraph[?]]
  val sRunnableGraphClass: Class[?] = classOf[pekko.stream.scaladsl.RunnableGraph[?]]

  val ignore: Set[String] =
    Set("equals", "hashCode", "notify", "notifyAll", "wait", "toString", "getClass") ++
    Set("productArity", "canEqual", "productPrefix", "copy", "productIterator", "productElement") ++
    Set("productElementName", "productElementNames") ++
    Set("_1") ++
    Set(
      "create",
      "apply",
      "ops",
      "appendJava",
      "andThen",
      "andThenMat",
      "isIdentity",
      "withAttributes",
      "transformMaterializing") ++
    Set("asScala", "asJava", "deprecatedAndThen", "deprecatedAndThenMat")

  val graphHelpers = Set(
    "zipGraph",
    "zipWithGraph",
    "zipLatestGraph",
    "zipLatestWithGraph",
    "zipAllFlow",
    "mergeGraph",
    "mergeLatestGraph",
    "mergePreferredGraph",
    "mergePrioritizedGraph",
    "mergeSortedGraph",
    "interleaveGraph",
    "concatGraph",
    "prependGraph",
    "alsoToGraph",
    "wireTapGraph",
    "orElseGraph",
    "divertToGraph",
    "flatten")

  val forComprehensions = Set("withFilter", "flatMap", "foreach")

  val allowMissing: Map[Class[?], Set[String]] = Map(
    jFlowClass -> (graphHelpers ++ forComprehensions),
    jSourceClass -> (graphHelpers ++ forComprehensions ++ Set("watch", "ask")),
    // Java subflows can only be nested using .via and .to (due to type system restrictions)
    jSubFlowClass -> (graphHelpers ++ forComprehensions ++ Set("groupBy", "splitAfter", "splitWhen", "subFlow", "watch",
      "ask")),
    jSubSourceClass -> (graphHelpers ++ forComprehensions ++ Set("groupBy", "splitAfter", "splitWhen", "subFlow",
      "watch", "ask")),
    sFlowClass -> Set("of"),
    sSourceClass -> Set("adapt", "from", "watch"),
    sSinkClass -> Set("adapt"),
    sSubFlowClass -> Set(),
    sSubSourceClass -> Set(),
    sRunnableGraphClass -> Set("builder"))

  @nowarn
  def materializing(m: Method): Boolean = m.getParameterTypes.contains(classOf[ActorMaterializer])

  def assertHasMethod(c: Class[?], name: String): Unit = {
    // include class name to get better error message
    if (!allowMissing.getOrElse(c, Set.empty).contains(name))
      c.getMethods.collect { case m if !ignore(m.getName) => c.getName + "." + m.getName } should contain(
        c.getName + "." + name)
  }

  "Java and Scala DSLs" must {

    (("Source" -> List[Class[?]](sSourceClass, jSourceClass)) ::
    ("SubSource" -> List[Class[?]](sSubSourceClass, jSubSourceClass)) ::
    ("Flow" -> List[Class[?]](sFlowClass, jFlowClass)) ::
    ("SubFlow" -> List[Class[?]](sSubFlowClass, jSubFlowClass)) ::
    ("Sink" -> List[Class[?]](sSinkClass, jSinkClass)) ::
    ("RunnableFlow" -> List[Class[?]](sRunnableGraphClass, jRunnableGraphClass)) ::
    Nil).foreach {
      case (element, classes) =>
        s"provide same $element transforming operators" in {
          val allOps =
            (for {
              c <- classes
              m <- c.getMethods
              if !Modifier.isStatic(m.getModifiers)
              if !ignore(m.getName)
              if !m.getName.contains("$")
              if !materializing(m)
            } yield m.getName).toSet

          for (c <- classes; op <- allOps)
            assertHasMethod(c, op)
        }

        s"provide same $element materializing operators" in {
          val materializingOps =
            (for {
              c <- classes
              m <- c.getMethods
              if !Modifier.isStatic(m.getModifiers)
              if !ignore(m.getName)
              if !m.getName.contains("$")
              if materializing(m)
            } yield m.getName).toSet

          for (c <- classes; op <- materializingOps)
            assertHasMethod(c, op)
        }

    }
  }

}
