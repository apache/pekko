/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.pekko.actor.testkit.typed.javadsl

import org.apache.pekko.actor.testkit.typed.annotations.Junit5TestKit
import org.junit.jupiter.api.extension.{ AfterAllCallback, BeforeTestExecutionCallback, ExtensionContext }
import org.junit.platform.commons.support.AnnotationSupport

final class TestKitJunit5Extension() extends AfterAllCallback with BeforeTestExecutionCallback {

  var testKit: Option[ActorTestKit] = None

  /**
   * Get a reference to the field annotated with @Junit5Testkit  [[Junit5TestKit]]
   */
  override def beforeTestExecution(context: ExtensionContext): Unit = {

    context.getTestInstance.ifPresent((instance: AnyRef) => {
      val annotations = AnnotationSupport.findAnnotatedFieldValues(instance, classOf[Junit5TestKit])
      val fieldValue = annotations.stream().findFirst().orElseThrow(() =>
        throw new IllegalArgumentException("Could not find field annotated with @Junit5TestKit"))
      testKit = Some(fieldValue.asInstanceOf[ActorTestKit])
    })
  }

  /**
   * Shutdown testKit
   */
  override def afterAll(context: ExtensionContext): Unit = {
    testKit.get.shutdownTestKit()
  }

}
