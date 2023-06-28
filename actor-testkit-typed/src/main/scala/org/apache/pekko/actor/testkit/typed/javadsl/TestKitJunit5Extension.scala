/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * license agreements; and to You under the Apache License, version 2.0:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is part of the Apache Pekko project, which was derived from Akka.
 */

package org.apache.pekko.actor.testkit.typed.javadsl

import org.junit.jupiter.api.extension.{AfterAllCallback, BeforeTestExecutionCallback, ExtensionContext}
import org.junit.platform.commons.support.AnnotationSupport
import org.apache.pekko
import org.apache.pekko.actor.testkit.typed.annotations.Junit5TestKit
import pekko.util.ccompat.JavaConverters.CollectionHasAsScala

final class TestKitJunit5Extension() extends AfterAllCallback with BeforeTestExecutionCallback {

  var testKit: Option[ActorTestKit] = None

  /**
   * Get a reference to the field annotated with @Junit5Testkit  [[Junit5TestKit]]
   */
  override def beforeTestExecution(context: ExtensionContext): Unit = {

    context.getTestInstance.ifPresent((instance: AnyRef) => {
      val annotations = AnnotationSupport.findAnnotatedFieldValues(instance, classOf[Junit5TestKit])
      if (annotations.isEmpty) {
        throw new IllegalArgumentException("Could not find field annotated with @Junit5TestKit")
      } else {
        val fieldValue = annotations.asScala.toList.head
        testKit = Some(fieldValue.asInstanceOf[ActorTestKit])
      }
    })
  }

  /**
   * Shutdown testKit
   */
  override def afterAll(context: ExtensionContext): Unit = {
    testKit.get.shutdownTestKit()
  }

}
