package org.apache.pekko.actor.testkit.typed.javadsl

import org.apache.pekko
import org.junit.jupiter.api.extension.{AfterAllCallback, BeforeTestExecutionCallback, ExtensionContext}
import org.junit.platform.commons.support.AnnotationSupport

import scala.jdk.CollectionConverters._

final class TestKitJunit5Extension() extends AfterAllCallback with BeforeTestExecutionCallback {

  var testKit: ActorTestKit = null


  /**
   * Get a reference to the field annotated with @Junit5Testkit  [[pekko.actor.testkit.typed.javadsl.Junit5TestKit]]
   *
   */
  override def beforeTestExecution(context: ExtensionContext): Unit =  {

    context.getTestInstance.ifPresent(instance => {
      val fielValue = AnnotationSupport.findAnnotatedFieldValues(instance, classOf[Junit5TestKit]).asScala.toList.head
      testKit = fielValue.asInstanceOf[ActorTestKit]
    })
  }


  /**
   * Shutdown testKit
   */
  override def afterAll(context: ExtensionContext): Unit = {
     testKit.shutdownTestKit()
  }

}
